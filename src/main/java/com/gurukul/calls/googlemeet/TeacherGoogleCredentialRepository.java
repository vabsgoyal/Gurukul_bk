package com.gurukul.calls.googlemeet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherGoogleCredentialRepository extends JpaRepository<TeacherGoogleCredential, UUID> {

	Optional<TeacherGoogleCredential> findByEmployeeId(UUID employeeId);

	void deleteByEmployeeId(UUID employeeId);

}
