package com.gurukul.idcards.repository;

import com.gurukul.idcards.entity.IdCardOwnerType;
import com.gurukul.idcards.entity.IdCardProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdCardProfileRepository extends JpaRepository<IdCardProfile, UUID> {

	Optional<IdCardProfile> findBySchoolIdAndOwnerTypeAndOwnerId(UUID schoolId, IdCardOwnerType ownerType, UUID ownerId);

	List<IdCardProfile> findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(UUID schoolId, IdCardOwnerType ownerType, Collection<UUID> ownerIds);

}
