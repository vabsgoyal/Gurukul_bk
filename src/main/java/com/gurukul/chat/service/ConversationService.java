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
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	private final ParentRepository parentRepository;
	private final ChatContactService chatContactService;

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
		boolean involvesParent = callerType == OwnerType.PARENT || otherType == OwnerType.PARENT;
		if (involvesParent) {
			requireParentPairingAllowed(principal, otherType, otherId);
		}

		return conversationRepository.findOneToOneBetween(schoolId, callerType, callerId, otherType, otherId)
				.orElseGet(() -> {
					ConversationType type;
					if (involvesParent) {
						type = ConversationType.PARENT_STAFF;
					} else if (callerType == OwnerType.EMPLOYEE && otherType == OwnerType.EMPLOYEE) {
						type = ConversationType.STAFF_STAFF;
					} else {
						type = ConversationType.STAFF_STUDENT;
					}
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
	 * Batch variant of {@link #participantsOf} for listing many conversations at once - one query
	 * for every conversation's participants instead of one query per conversation (this was the
	 * N+1 behind slow chat-list loads: {@code listForCaller} then a per-conversation lookup).
	 */
	public Map<UUID, List<ConversationParticipant>> participantsOf(List<UUID> conversationIds) {
		if (conversationIds.isEmpty()) {
			return Map.of();
		}
		return conversationParticipantRepository.findAllByConversation_IdIn(conversationIds).stream()
				.collect(Collectors.groupingBy(p -> p.getConversation().getId()));
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

	/**
	 * A parent may only ever pair with staff, and only with the staff ChatContactService allows (their
	 * children's teachers, the school's admins); staff may only start with parents of their students
	 * (an admin: any parent of the school). Student-parent and parent-parent are refused outright.
	 * AccessDenied rather than a 400: the request is well-formed, the caller just may not reach that
	 * person.
	 */
	private void requireParentPairingAllowed(AuthPrincipal principal, OwnerType otherType, UUID otherId) {
		OwnerType callerType = principal.getOwnerType();
		boolean allowed;
		if (callerType == OwnerType.PARENT) {
			allowed = otherType == OwnerType.EMPLOYEE
					&& chatContactService.parentMayContactEmployee(principal.getSchoolId(), principal.getOwnerId(), otherId);
		} else if (callerType == OwnerType.EMPLOYEE) {
			allowed = chatContactService.staffMayContactParent(principal, otherId);
		} else {
			allowed = false;
		}
		if (!allowed) {
			throw new AccessDeniedException(callerType == OwnerType.PARENT
					? "You can message your child's teachers and the school's admins only"
					: "You can message parents of your own students only");
		}
	}

	private void requireExists(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		boolean exists = switch (ownerType) {
			case EMPLOYEE -> employeeRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
			case STUDENT -> studentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
			case PARENT -> parentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
		};
		if (!exists) {
			throw new EntityNotFoundException(switch (ownerType) {
				case EMPLOYEE -> "Employee not found";
				case STUDENT -> "Student not found";
				case PARENT -> "Parent not found";
			});
		}
	}

	/**
	 * Display names for every participant, one query per owner type - so the app can name the other
	 * party (a parent in particular, who isn't in any directory the app can list) without loading the
	 * whole school's employee/student lists.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, String> namesOf(UUID schoolId, Collection<ConversationParticipant> participants) {
		Map<OwnerType, Set<UUID>> idsByType = new EnumMap<>(OwnerType.class);
		for (ConversationParticipant participant : participants) {
			idsByType.computeIfAbsent(participant.getOwnerType(), t -> new HashSet<>()).add(participant.getOwnerId());
		}
		Map<UUID, String> names = new HashMap<>();
		idsByType.forEach((ownerType, ids) -> {
			switch (ownerType) {
				case EMPLOYEE -> employeeRepository.findAllBySchoolIdAndIdIn(schoolId, ids)
						.forEach(e -> names.put(e.getId(), e.getName()));
				case STUDENT -> studentRepository.findAllBySchoolIdAndIdIn(schoolId, ids)
						.forEach(st -> names.put(st.getId(), st.getName()));
				case PARENT -> parentRepository.findAllBySchoolIdAndIdIn(schoolId, ids)
						.forEach(pa -> names.put(pa.getId(), pa.getName()));
			}
		});
		return names;
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
