package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "student_game_profile", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "student_id"})
})
public class StudentGameProfile extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "total_xp", nullable = false)
	private long totalXp;

	@Column(name = "current_streak_days", nullable = false)
	private int currentStreakDays;

	@Column(name = "longest_streak_days", nullable = false)
	private int longestStreakDays;

}
