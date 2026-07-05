package com.gurukul.finance.repository;

import com.gurukul.finance.entity.FundAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundAccountRepository extends JpaRepository<FundAccount, UUID> {

	List<FundAccount> findAllBySchoolId(UUID schoolId);

	Optional<FundAccount> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<FundAccount> findBySchoolIdAndCode(UUID schoolId, String code);

}
