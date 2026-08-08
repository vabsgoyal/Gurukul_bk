package com.gurukul.employees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.dto.EmployeeFeedbackRequest;
import com.gurukul.employees.dto.EmployeeFeedbackResponse;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeFeedback;
import com.gurukul.employees.repository.EmployeeFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeFeedbackService {

	private final EmployeeFeedbackRepository employeeFeedbackRepository;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;

	public List<EmployeeFeedbackResponse> list(UUID employeeId) {
		UUID schoolId = schoolContext.getSchoolId();
		employeeService.getScopedEntity(employeeId);
		return employeeFeedbackRepository.findAllBySchoolIdAndEmployeeIdOrderByFeedbackDateDesc(schoolId, employeeId)
				.stream().map(EmployeeFeedbackResponse::from).toList();
	}

	@Transactional
	public EmployeeFeedbackResponse create(UUID employeeId, EmployeeFeedbackRequest request) {
		Employee employee = employeeService.getScopedEntity(employeeId);
		EmployeeFeedback feedback = new EmployeeFeedback();
		feedback.setSchoolId(schoolContext.getSchoolId());
		feedback.setEmployee(employee);
		applyRequest(feedback, request);
		return EmployeeFeedbackResponse.from(employeeFeedbackRepository.save(feedback));
	}

	@Transactional
	public EmployeeFeedbackResponse update(UUID employeeId, UUID feedbackId, EmployeeFeedbackRequest request) {
		EmployeeFeedback feedback = findScoped(employeeId, feedbackId);
		applyRequest(feedback, request);
		return EmployeeFeedbackResponse.from(employeeFeedbackRepository.save(feedback));
	}

	@Transactional
	public void delete(UUID employeeId, UUID feedbackId) {
		employeeFeedbackRepository.delete(findScoped(employeeId, feedbackId));
	}

	private void applyRequest(EmployeeFeedback feedback, EmployeeFeedbackRequest request) {
		feedback.setRating(request.getRating());
		feedback.setCategory(request.getCategory());
		feedback.setComment(request.getComment());
		feedback.setFeedbackDate(request.getFeedbackDate());
		feedback.setSubmittedBy(request.getSubmittedBy());
	}

	private EmployeeFeedback findScoped(UUID employeeId, UUID feedbackId) {
		EmployeeFeedback feedback = employeeFeedbackRepository.findByIdAndSchoolId(feedbackId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Feedback not found"));
		if (!feedback.getEmployee().getId().equals(employeeId)) {
			throw new EntityNotFoundException("Feedback not found for this employee");
		}
		return feedback;
	}

}
