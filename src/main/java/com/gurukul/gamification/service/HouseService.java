package com.gurukul.gamification.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.gamification.dto.HouseDtos.AwardSpotRecognitionRequest;
import com.gurukul.gamification.dto.HouseDtos.CreateHouseRequest;
import com.gurukul.gamification.dto.HouseDtos.HouseResponse;
import com.gurukul.gamification.dto.HouseDtos.HouseStandingResponse;
import com.gurukul.gamification.dto.HouseDtos.HouseWarsResponse;
import com.gurukul.gamification.dto.HouseDtos.SpotRecognitionFeedItem;
import com.gurukul.gamification.entity.House;
import com.gurukul.gamification.entity.HouseMembership;
import com.gurukul.gamification.entity.HousePointEvent;
import com.gurukul.gamification.repository.HouseMembershipRepository;
import com.gurukul.gamification.repository.HousePointEventRepository;
import com.gurukul.gamification.repository.HouseRepository;
import com.gurukul.gamification.repository.XpEventRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * House Wars (Phase 3 of specs/gamification/execution-plan.md). A house's total is the sum of
 * its members' all-time XP (computed live, same pattern as Phase 2's live weekly league XP - no
 * counter to keep in sync) plus teacher-awarded "spot recognition" points, which are the only
 * thing actually persisted here. Visible to every role in the school per the answered question
 * ("everyone" can see the school-wide House leaderboard).
 */
@Service
@RequiredArgsConstructor
public class HouseService {

	private final HouseRepository houseRepository;
	private final HouseMembershipRepository houseMembershipRepository;
	private final HousePointEventRepository housePointEventRepository;
	private final XpEventRepository xpEventRepository;
	private final StudentRepository studentRepository;

	@Transactional
	public HouseResponse createHouse(AuthPrincipal principal, CreateHouseRequest request) {
		requireAdmin(principal);
		UUID schoolId = principal.getSchoolId();
		if (houseRepository.existsBySchoolIdAndName(schoolId, request.getName())) {
			throw new IllegalArgumentException("A house with this name already exists");
		}
		House house = new House();
		house.setSchoolId(schoolId);
		house.setName(request.getName());
		house.setColorHex(request.getColorHex());
		return HouseResponse.from(houseRepository.save(house));
	}

	public List<HouseResponse> listHouses(AuthPrincipal principal) {
		return houseRepository.findAllBySchoolIdOrderByNameAsc(principal.getSchoolId()).stream()
				.map(HouseResponse::from)
				.toList();
	}

	@Transactional
	public HouseWarsResponse getHouseWars(AuthPrincipal principal) {
		UUID schoolId = principal.getSchoolId();
		List<House> houses = houseRepository.findAllBySchoolIdOrderByNameAsc(schoolId);

		List<HouseStandingResponse> standings = houses.stream()
				.map(house -> {
					long memberCount = houseMembershipRepository.countBySchoolIdAndHouseId(schoolId, house.getId());
					long xpTotal = houseMembershipRepository.findAllBySchoolIdAndHouseId(schoolId, house.getId()).stream()
							.mapToLong(m -> xpEventRepository.sumAmountSince(m.getStudentId(), Instant.EPOCH))
							.sum();
					long spotTotal = housePointEventRepository.sumAmountForHouse(house.getId());
					return new HouseStandingResponse(
							house.getId(), house.getName(), house.getColorHex(), xpTotal + spotTotal, memberCount);
				})
				.sorted(Comparator.comparingLong(HouseStandingResponse::getTotalPoints).reversed())
				.toList();

		List<SpotRecognitionFeedItem> feed = housePointEventRepository.findTop15BySchoolIdOrderByCreatedAtDesc(schoolId)
				.stream()
				.map(event -> new SpotRecognitionFeedItem(
						studentRepository.findByIdAndSchoolId(event.getStudentId(), schoolId).map(Student::getName).orElse("Unknown"),
						event.getHouse().getName(),
						event.getAmount(),
						event.getReason(),
						event.getCreatedAt()))
				.toList();

		UUID yourHouseId = principal.getOwnerType() == OwnerType.STUDENT
				? getOrAssignHouse(schoolId, principal.getOwnerId()).map(m -> m.getHouse().getId()).orElse(null)
				: null;

		return new HouseWarsResponse(standings, feed, yourHouseId);
	}

	@Transactional
	public void awardSpotRecognition(AuthPrincipal principal, AwardSpotRecognitionRequest request) {
		if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.TEACHER) {
			throw new AccessDeniedException("Only a teacher or admin can award spot recognition");
		}
		UUID schoolId = principal.getSchoolId();
		if (studentRepository.findByIdAndSchoolId(request.getStudentId(), schoolId).isEmpty()) {
			throw new EntityNotFoundException("Student not found");
		}
		HouseMembership membership = getOrAssignHouse(schoolId, request.getStudentId())
				.orElseThrow(() -> new IllegalStateException("No houses have been set up for this school yet"));

		HousePointEvent event = new HousePointEvent();
		event.setSchoolId(schoolId);
		event.setHouse(membership.getHouse());
		event.setStudentId(request.getStudentId());
		event.setAmount(request.getAmount());
		event.setReason(request.getReason());
		event.setAwardedByEmployeeId(principal.getOwnerId());
		housePointEventRepository.save(event);
	}

	/** Auto-assigns to whichever house currently has the fewest members, if the student doesn't have one yet. */
	private Optional<HouseMembership> getOrAssignHouse(UUID schoolId, UUID studentId) {
		Optional<HouseMembership> existing = houseMembershipRepository.findBySchoolIdAndStudentId(schoolId, studentId);
		if (existing.isPresent()) {
			return existing;
		}
		List<House> houses = houseRepository.findAllBySchoolIdOrderByNameAsc(schoolId);
		if (houses.isEmpty()) {
			return Optional.empty();
		}
		House smallest = houses.stream()
				.min(Comparator.comparingLong(h -> houseMembershipRepository.countBySchoolIdAndHouseId(schoolId, h.getId())))
				.orElseThrow();
		HouseMembership membership = new HouseMembership();
		membership.setSchoolId(schoolId);
		membership.setHouse(smallest);
		membership.setStudentId(studentId);
		return Optional.of(houseMembershipRepository.save(membership));
	}

	private void requireAdmin(AuthPrincipal principal) {
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can do this");
		}
	}

}
