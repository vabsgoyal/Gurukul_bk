package com.gurukul.chat.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	Optional<Conversation> findByIdAndSchoolId(UUID id, UUID schoolId);

	/**
	 * A 1:1 conversation has exactly two participant rows by construction (see ConversationService),
	 * so matching both EXISTS clauses against the same conversation uniquely identifies it.
	 */
	@Query("""
			SELECT c FROM Conversation c
			WHERE c.schoolId = :schoolId
			AND EXISTS (SELECT 1 FROM ConversationParticipant p1
			            WHERE p1.conversation = c AND p1.ownerType = :ownerType1 AND p1.ownerId = :ownerId1)
			AND EXISTS (SELECT 1 FROM ConversationParticipant p2
			            WHERE p2.conversation = c AND p2.ownerType = :ownerType2 AND p2.ownerId = :ownerId2)
			""")
	Optional<Conversation> findOneToOneBetween(
			@Param("schoolId") UUID schoolId,
			@Param("ownerType1") OwnerType ownerType1, @Param("ownerId1") UUID ownerId1,
			@Param("ownerType2") OwnerType ownerType2, @Param("ownerId2") UUID ownerId2);

	/**
	 * A BOT conversation has exactly one participant row (the human) - identifies the caller's
	 * own bot conversation for the get-or-create endpoint.
	 */
	@Query("""
			SELECT c FROM Conversation c
			WHERE c.schoolId = :schoolId AND c.type = com.gurukul.chat.entity.ConversationType.BOT
			AND EXISTS (SELECT 1 FROM ConversationParticipant p
			            WHERE p.conversation = c AND p.ownerType = :ownerType AND p.ownerId = :ownerId)
			""")
	Optional<Conversation> findBotConversationFor(
			@Param("schoolId") UUID schoolId, @Param("ownerType") OwnerType ownerType, @Param("ownerId") UUID ownerId);

	@Query("""
			SELECT DISTINCT c FROM Conversation c
			WHERE c.schoolId = :schoolId
			AND EXISTS (SELECT 1 FROM ConversationParticipant p
			            WHERE p.conversation = c AND p.ownerType = :ownerType AND p.ownerId = :ownerId)
			ORDER BY c.updatedAt DESC
			""")
	java.util.List<Conversation> findAllForOwner(
			@Param("schoolId") UUID schoolId, @Param("ownerType") OwnerType ownerType, @Param("ownerId") UUID ownerId);

}
