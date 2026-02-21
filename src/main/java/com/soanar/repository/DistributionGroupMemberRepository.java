package com.soanar.repository;

import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistributionGroupMemberRepository extends JpaRepository<DistributionGroupMember, Long> {
    List<DistributionGroupMember> findByGroupId(Long groupId);
    boolean existsByGroupAndStudentEmail(DistributionGroup group, String studentEmail);
}
