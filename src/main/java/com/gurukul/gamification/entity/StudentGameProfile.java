package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	/**
	 * This week's league tier - weekly XP itself is never stored here, it's always computed live
	 * from XpEvent for the current week (see LeagueService), so there's nothing to keep in sync
	 * when XP is awarded from any source. Only the tier, which the weekly promotion sweep updates,
	 * needs to persist.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "current_tier", nullable = false)
	private LeagueTier currentTier = LeagueTier.BRONZE;

}
