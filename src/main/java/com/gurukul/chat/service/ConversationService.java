package com.gurukul.chat.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.ChatDtos.CreateConversationRequest;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationParticipant;
import com.gurukul.chat.entity.ConversationType;
import com.gurukul.chat.repository.ConversationParticipantRepository;
import com.gurukul.chat.repository.ConversationRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Every method here takes an explicit {@link AuthPrincipal} and never reads
 * {@code AuthContext.current()} / {@code SchoolContext.getSchoolId()} internally - unlike most
 * services in this codebase, this one must work identically whether it's called from an ordinary
 * HTTP thread (REST controllers) or a STOMP message-handling thread (ChatMessageController),
 * where those per-request ThreadLocals are never populated. See the Chat Integration plan for why.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

	private final ConversationRepository conversationRepository;
	private final ConversationParticipantRepository conversationParticipantRepository;
	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;

	@Transactional
	public Conversation createOneToOne(AuthPrincipal principal, CreateConversationRequest request) {
		UUID schoolId = principal.getSchoolId();
		OwnerType callerType = principal.getOwnerType();
		UUID callerId = principal.getOwnerId();
		OwnerType otherType = request.getOtherPartyOwnerType();
		UUID otherId = request.getOtherPartyOwnerId();

		if (callerType == otherType && callerId.equals(otherId)) {
			throw new IllegalArgumentException("Cannot start a conversation with yourself");
		}
		if (callerType == OwnerType.STUDENT && otherType == OwnerType.STUDENT) {
			throw new IllegalArgumentException("Student-to-student messaging is not supported");
		}
		requireExists(schoolId, otherType, otherId);

		return conversationRepository.findOneToOneBetween(schoolId, callerType, callerId, otherType, otherId)
				.orElseGet(() -> {
					ConversationType type = (callerType == OwnerType.EMPLOYEE && otherType == OwnerType.EMPLOYEE)
							? ConversationType.STAFF_STAFF
							: ConversationType.STAFF_STUDENT;
					Conversation conversation = newConversation(schoolId, type);
					addParticipant(conversation, callerType, callerId);
					addParticipant(conversation, otherType, otherId);
					return conversation;
				});
	}

	@Transactional
	public Conversation getOrCreateBotConversation(AuthPrincipal principal) {
		UUID schoolId = principal.getSchoolId();
		return conversationRepository
				.findBotConversationFor(schoolId, principal.getOwnerType(), principal.getOwnerId())
				.orElseGet(() -> {
					Conversation conversation = newConversation(schoolId, ConversationType.BOT);
					addParticipant(conversation, principal.getOwnerType(), principal.getOwnerId());
					return conversation;
				});
	}

	public List<Conversation> listForCaller(AuthPrincipal principal) {
		return conversationRepository.findAllForOwner(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
	}

	public List<ConversationParticipant> participantsOf(UUID conversationId) {
		return conversationParticipantRepository.findAllByConversation_Id(conversationId);
	}

	/**
	 * Loads the conversation (scoped to the caller's school) and verifies the caller is one of its
	 * participants. This is the single enforcement point for "may this principal read/send in this
	 * conversation" - used both by the REST history endpoint and by ChatMessageController before
	 * accepting a live send.
	 */
	public Conversation requireParticipant(AuthPrincipal principal, UUID conversationId) {
		Conversation conversation = conversationRepository.findByIdAndSchoolId(conversationId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Conversation not found"));
		boolean isParticipant = conversationParticipantRepository.existsByConversation_IdAndOwnerTypeAndOwnerId(
				conversationId, principal.getOwnerType(), principal.getOwnerId());
		if (!isParticipant) {
			throw new AccessDeniedException("You are not a participant of this conversation");
		}
		return conversation;
	}

	private void requireExists(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		boolean exists = ownerType == OwnerType.EMPLOYEE
				? employeeRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent()
				: studentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
		if (!exists) {
			throw new EntityNotFoundException(
					(ownerType == OwnerType.EMPLOYEE ? "Employee" : "Student") + " not found");
		}
	}

	private Conversation newConversation(UUID schoolId, ConversationType type) {
		Conversation conversation = new Conversation();
		conversation.setSchoolId(schoolId);
		conversation.setType(type);
		return conversationRepository.save(conversation);
	}

	private void addParticipant(Conversation conversation, OwnerType ownerType, UUID ownerId) {
		ConversationParticipant participant = new ConversationParticipant();
		participant.setSchoolId(conversation.getSchoolId());
		participant.setConversation(conversation);
		participant.setOwnerType(ownerType);
		participant.setOwnerId(ownerId);
		conversationParticipantRepository.save(participant);
	}

}
