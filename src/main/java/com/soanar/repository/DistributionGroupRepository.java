package com.soanar.repository;

import com.soanar.model.DistributionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistributionGroupRepository extends JpaRepository<DistributionGroup, Long> {
    List<DistributionGroup> findByOrganizationId(String organizationId);
}
