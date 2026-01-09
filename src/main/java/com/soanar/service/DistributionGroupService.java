package com.soanar.service;

import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.repository.DistributionGroupRepository;
import com.soanar.repository.DistributionGroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DistributionGroupService {

    private final DistributionGroupRepository groupRepository;
    private final DistributionGroupMemberRepository memberRepository;

    public DistributionGroupService(DistributionGroupRepository groupRepository,
                                     DistributionGroupMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    public List<DistributionGroup> getAllGroups() {
        return groupRepository.findAll();
    }

    @Transactional
    public DistributionGroup createGroup(String name) {
        DistributionGroup group = new DistributionGroup();
        group.setName(name);
        return groupRepository.save(group);
    }

    @Transactional
    public void addMemberToGroup(Long groupId, String studentEmail) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        
        DistributionGroupMember member = new DistributionGroupMember();
        member.setGroup(group);
        member.setStudentEmail(studentEmail);
        memberRepository.save(member);
    }

    public List<String> getGroupMemberEmails(Long groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .map(DistributionGroupMember::getStudentEmail)
                .collect(Collectors.toList());
    }
}
