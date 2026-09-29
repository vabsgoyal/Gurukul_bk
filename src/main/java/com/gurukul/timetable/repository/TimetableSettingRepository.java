package com.gurukul.timetable.repository;

import com.gurukul.timetable.entity.TimetableSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TimetableSettingRepository extends JpaRepository<TimetableSetting, UUID> {

	Optional<TimetableSetting> findBySchoolId(UUID schoolId);

}
