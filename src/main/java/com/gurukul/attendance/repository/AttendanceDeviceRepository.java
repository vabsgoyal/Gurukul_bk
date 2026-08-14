package com.gurukul.attendance.repository;

import com.gurukul.attendance.entity.AttendanceDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDevice, UUID> {

	List<AttendanceDevice> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	List<AttendanceDevice> findAllBySchoolIdAndActiveTrue(UUID schoolId);

	Optional<AttendanceDevice> findByIdAndSchoolId(UUID id, UUID schoolId);

}
