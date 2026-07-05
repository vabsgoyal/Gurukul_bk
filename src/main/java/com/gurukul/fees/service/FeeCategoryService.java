package com.gurukul.fees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeeCategoryRequest;
import com.gurukul.fees.dto.FeeCategoryResponse;
import com.gurukul.fees.entity.FeeCategory;
import com.gurukul.fees.repository.FeeCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeeCategoryService {

	private final FeeCategoryRepository feeCategoryRepository;
	private final SchoolContext schoolContext;

	public List<FeeCategoryResponse> list() {
		return feeCategoryRepository.findAllBySchoolIdOrderByCodeAsc(schoolContext.getSchoolId()).stream()
				.map(FeeCategoryResponse::from)
				.toList();
	}

	@Transactional
	public FeeCategoryResponse create(FeeCategoryRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (feeCategoryRepository.findBySchoolIdAndCode(schoolId, request.getCode()).isPresent()) {
			throw new IllegalArgumentException("Fee category code already exists");
		}
		FeeCategory category = new FeeCategory();
		category.setSchoolId(schoolId);
		category.setCode(request.getCode());
		category.setName(request.getName());
		return FeeCategoryResponse.from(feeCategoryRepository.save(category));
	}

	public FeeCategory getScopedEntity(UUID id) {
		return feeCategoryRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee category not found"));
	}

}
