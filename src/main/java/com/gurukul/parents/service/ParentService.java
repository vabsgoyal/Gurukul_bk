package com.gurukul.parents.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.dto.StudentResponse;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParentService {

	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final StudentRepository studentRepository;

	@Transactional(readOnly = true)
	public List<StudentResponse> listMyChildren(UUID parentId) {
		// A parent already has the child's registrationNumber (they needed it to register in the
		// first place) - it isn't re-shown here, same as it's hidden from every other non-admin caller.
		return parentStudentLinkRepository.findAllByParentId(parentId).stream()
				.map(link -> studentRepository.findById(link.getStudentId()).orElse(null))
				.filter(Objects::nonNull)
				.map(s -> StudentResponse.from(s, false))
				.toList();
	}

	/** Every parent-facing "give me my child's data" endpoint must call this first - the whole point of Parent being a separate identity now. */
	@Transactional(readOnly = true)
	public Student requireLinkedChild(UUID parentId, UUID studentId, UUID schoolId) {
		if (!parentStudentLinkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
			throw new AccessDeniedException("This student is not linked to your account");
		}
		return studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
	}

}
