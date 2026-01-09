package com.soanar.repository;

import com.soanar.model.DistributionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DistributionGroupRepository extends JpaRepository<DistributionGroup, Long> {
}
