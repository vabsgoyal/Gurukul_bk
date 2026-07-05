package com.gurukul.expenses.infrastructure.repository;

import com.gurukul.expenses.infrastructure.entity.InfraExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InfraExpenseCategoryRepository extends JpaRepository<InfraExpenseCategory, UUID> {

	List<InfraExpenseCategory> findAllBySchoolIdOrderByCodeAsc(UUID schoolId);

}
