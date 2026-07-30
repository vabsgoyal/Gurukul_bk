package com.gurukul.auth.repository;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

	Optional<Credential> findBySchoolIdAndUsername(UUID schoolId, String username);

	boolean existsBySchoolIdAndUsername(UUID schoolId, String username);

	boolean existsByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

	Optional<Credential> findByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

	boolean existsBySchoolIdAndRole(UUID schoolId, Role role);

	List<Credential> findAllBySchoolIdAndOwnerType(UUID schoolId, OwnerType ownerType);

}
