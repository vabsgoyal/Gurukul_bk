package com.gurukul.exams.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.exams.dto.GradingBandDtos.GradingBandRequest;
import com.gurukul.exams.dto.GradingBandDtos.GradingBandResponse;
import com.gurukul.exams.entity.GradingBand;
import com.gurukul.exams.repository.GradingBandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Marks-percentage -> letter-grade bands, configurable per school. A school that never configures
 * its own bands (the common case, especially early on) falls back to {@link #DEFAULT_BANDS} -
 * every grade card still shows a letter grade, not just a raw percentage.
 */
@Service
@RequiredArgsConstructor
public class GradingScaleService {

	private record DefaultBand(BigDecimal min, BigDecimal max, String label) {
	}

	private static final List<DefaultBand> DEFAULT_BANDS = List.of(
			new DefaultBand(BigDecimal.valueOf(90), BigDecimal.valueOf(100), "A+"),
			new DefaultBand(BigDecimal.valueOf(75), BigDecimal.valueOf(90), "A"),
			new DefaultBand(BigDecimal.valueOf(60), BigDecimal.valueOf(75), "B"),
			new DefaultBand(BigDecimal.valueOf(45), BigDecimal.valueOf(60), "C"),
			new DefaultBand(BigDecimal.valueOf(33), BigDecimal.valueOf(45), "D"),
			new DefaultBand(BigDecimal.valueOf(0), BigDecimal.valueOf(33), "F")
	);

	private static final String DEFAULT_FALLBACK_LABEL = "F";

	private final GradingBandRepository gradingBandRepository;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<GradingBandResponse> list() {
		List<GradingBand> bands = gradingBandRepository.findAllBySchoolIdOrderByMinPercentageDesc(schoolContext.getSchoolId());
		if (!bands.isEmpty()) {
			return bands.stream().map(GradingBandResponse::from).toList();
		}
		return DEFAULT_BANDS.stream()
				.map(b -> new GradingBandResponse(null, b.min(), b.max(), b.label()))
				.toList();
	}

	@Transactional
	public List<GradingBandResponse> replaceBands(List<GradingBandRequest> requests) {
		UUID schoolId = schoolContext.getSchoolId();
		gradingBandRepository.deleteAllBySchoolId(schoolId);
		List<GradingBand> saved = requests.stream()
				.map(r -> {
					GradingBand band = new GradingBand();
					band.setSchoolId(schoolId);
					band.setMinPercentage(r.getMinPercentage());
					band.setMaxPercentage(r.getMaxPercentage());
					band.setLabel(r.getLabel());
					return gradingBandRepository.save(band);
				})
				.sorted(Comparator.comparing(GradingBand::getMinPercentage).reversed())
				.toList();
		return saved.stream().map(GradingBandResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public String resolveGrade(BigDecimal percentage) {
		List<GradingBand> bands = gradingBandRepository.findAllBySchoolIdOrderByMinPercentageDesc(schoolContext.getSchoolId());
		if (!bands.isEmpty()) {
			return bands.stream()
					.filter(b -> percentage.compareTo(b.getMinPercentage()) >= 0)
					.findFirst()
					.map(GradingBand::getLabel)
					.orElse(DEFAULT_FALLBACK_LABEL);
		}
		return DEFAULT_BANDS.stream()
				.filter(b -> percentage.compareTo(b.min()) >= 0)
				.findFirst()
				.map(DefaultBand::label)
				.orElse(DEFAULT_FALLBACK_LABEL);
	}

}
