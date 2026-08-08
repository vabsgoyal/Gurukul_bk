package com.gurukul.teachers.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits maxMarks evenly across questionCount questions, handing the remainder to the first
 * questions one mark at a time, so the total always sums to exactly maxMarks regardless of
 * which {@link TeacherAiQuizGenerator} produced the questions.
 */
final class QuizMarksAllocator {

	private QuizMarksAllocator() {
	}

	static List<Integer> distribute(int maxMarks, int questionCount) {
		int baseMarks = Math.max(1, maxMarks / questionCount);
		int remainder = maxMarks - (baseMarks * questionCount);
		List<Integer> marks = new ArrayList<>(questionCount);
		for (int i = 0; i < questionCount; i++) {
			int value = baseMarks + (remainder > 0 ? 1 : 0);
			if (remainder > 0) {
				remainder--;
			}
			marks.add(value);
		}
		return marks;
	}

}
