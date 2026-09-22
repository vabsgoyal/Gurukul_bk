package com.gurukul.students.service;

import com.gurukul.students.dto.StudentBulkImportResponse;
import com.gurukul.students.dto.StudentBulkImportResponse.RowResult;
import com.gurukul.students.dto.StudentRequest;
import com.gurukul.students.entity.Student;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Separate bean from StudentService on purpose: each row must go through
 * {@link StudentService#createEntity} as its own transaction, so one bad row doesn't roll back rows
 * already committed earlier in the batch. Calling createEntity from a method on StudentService itself
 * (self-invocation) would bypass Spring's transactional proxy and lose that per-row isolation - going
 * through a different bean's injected reference keeps it.
 */
@Service
@RequiredArgsConstructor
public class StudentBulkImportService {

	private final StudentService studentService;
	private final Validator validator;

	public StudentBulkImportResponse importAll(List<StudentRequest> requests) {
		List<RowResult> results = new ArrayList<>(requests.size());
		int succeeded = 0;

		for (int i = 0; i < requests.size(); i++) {
			StudentRequest row = requests.get(i);
			String name = row.getName();

			Set<ConstraintViolation<StudentRequest>> violations = validator.validate(row);
			if (!violations.isEmpty()) {
				String message = violations.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining("; "));
				results.add(RowResult.failure(i, name, message));
				continue;
			}

			try {
				Student saved = studentService.createEntity(row);
				results.add(RowResult.success(i, name, saved.getId()));
				succeeded++;
			} catch (Exception e) {
				results.add(RowResult.failure(i, name, e.getMessage()));
			}
		}

		return new StudentBulkImportResponse(requests.size(), succeeded, requests.size() - succeeded, results);
	}

}
