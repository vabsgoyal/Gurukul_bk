package com.gurukul.gamification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BattleRoomScoringTest {

	@Test
	void correctAnswersScoreTenDownToOneBySecond() {
		assertThat(BattleRoomService.pointsFor(0)).isEqualTo(10);
		assertThat(BattleRoomService.pointsFor(999)).isEqualTo(10);
		assertThat(BattleRoomService.pointsFor(1_000)).isEqualTo(9);
		assertThat(BattleRoomService.pointsFor(4_500)).isEqualTo(6);
		assertThat(BattleRoomService.pointsFor(8_999)).isEqualTo(2);
		assertThat(BattleRoomService.pointsFor(9_000)).isEqualTo(1);
	}

	@Test
	void neverDropsBelowOneForACorrectAnswer() {
		assertThat(BattleRoomService.pointsFor(9_999)).isEqualTo(1);
		assertThat(BattleRoomService.pointsFor(10_000)).isEqualTo(1);
		assertThat(BattleRoomService.pointsFor(60_000)).isEqualTo(1);
	}

}
