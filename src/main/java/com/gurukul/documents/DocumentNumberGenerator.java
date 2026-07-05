package com.gurukul.documents;

import com.gurukul.finance.entity.ReceiptSequence;
import com.gurukul.finance.entity.ReceiptSequenceType;
import com.gurukul.finance.repository.ReceiptSequenceRepository;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentNumberGenerator {

	private final ReceiptSequenceRepository receiptSequenceRepository;
	private final SchoolRepository schoolRepository;

	public String nextReceiptNumber(UUID schoolId, ReceiptSequenceType type, String academicYear) {
		ReceiptSequence sequence = receiptSequenceRepository
				.findForUpdate(schoolId, type, academicYear)
				.orElseGet(() -> createSequence(schoolId, type, academicYear));

		long next = sequence.getLastValue() + 1;
		sequence.setLastValue(next);
		receiptSequenceRepository.save(sequence);

		String schoolCode = deriveSchoolCode(schoolId);
		return String.format("%s/%s/%s/%06d", schoolCode, academicYear, type.name(), next);
	}

	private ReceiptSequence createSequence(UUID schoolId, ReceiptSequenceType type, String academicYear) {
		ReceiptSequence sequence = new ReceiptSequence();
		sequence.setSchoolId(schoolId);
		sequence.setSequenceType(type);
		sequence.setAcademicYear(academicYear);
		sequence.setLastValue(0);
		Instant now = Instant.now();
		sequence.setCreatedAt(now);
		sequence.setUpdatedAt(now);
		return receiptSequenceRepository.save(sequence);
	}

	private String deriveSchoolCode(UUID schoolId) {
		return schoolRepository.findById(schoolId)
				.map(School::getName)
				.map(name -> name.replaceAll("[^A-Za-z]", "").toUpperCase())
				.filter(code -> !code.isEmpty())
				.map(code -> code.length() >= 3 ? code.substring(0, 3) : code)
				.orElse("SCH");
	}

}
