package com.gurukul.teachers.repository;

import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.entity.TeacherStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

	List<Teacher> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Optional<Teacher> findByIdAndSchoolId(UUID id, UUID schoolId);

	boolean existsBySchoolIdAndEmployeeCode(UUID schoolId, String employeeCode);

	boolean existsBySchoolIdAndEmployeeCodeAndIdNot(UUID schoolId, String employeeCode, UUID id);

	boolean existsBySchoolIdAndEmail(UUID schoolId, String email);

	boolean existsBySchoolIdAndEmailAndIdNot(UUID schoolId, String email, UUID id);

	long countBySchoolId(UUID schoolId);

	long countBySchoolIdAndStatus(UUID schoolId, TeacherStatus status);

}
