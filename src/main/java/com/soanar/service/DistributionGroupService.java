package com.soanar.service;

import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.model.User;
import com.soanar.repository.DistributionGroupRepository;
import com.soanar.repository.DistributionGroupMemberRepository;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DistributionGroupService {

    private final DistributionGroupRepository groupRepository;
    private final DistributionGroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    public DistributionGroupService(DistributionGroupRepository groupRepository,
                                     DistributionGroupMemberRepository memberRepository,
                                     UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    public List<DistributionGroup> getAllGroups() {
        return groupRepository.findAll();
    }

    public List<DistributionGroup> getGroupsByOrganization(String organizationId) {
        return groupRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public DistributionGroup createGroup(String name) {
        DistributionGroup group = new DistributionGroup();
        group.setName(name);
        return groupRepository.save(group);
    }

    @Transactional
    public DistributionGroup createGroup(String organizationId, String name, String description, User creator) {
        DistributionGroup group = new DistributionGroup();
        group.setOrganizationId(organizationId);
        group.setName(name);
        group.setDescription(description);
        group.setCreatedBy(creator);
        return groupRepository.save(group);
    }

    @Transactional
    public DistributionGroup updateGroup(Long groupId, String name, String description) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));
        
        if (name != null && !name.isBlank()) {
            group.setName(name);
        }
        if (description != null) {
            group.setDescription(description);
        }
        
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));
        groupRepository.delete(group);
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

    @Transactional
    public DistributionGroupMember addMember(Long groupId, String studentEmail) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));

        User user = userRepository.findBySchoolEmail(studentEmail).orElse(null);

        boolean alreadyExists = memberRepository.existsByGroupAndStudentEmail(group, studentEmail);
        if (alreadyExists) {
            throw new RuntimeException("Student already exists in this group");
        }

        DistributionGroupMember member = new DistributionGroupMember();
        member.setGroup(group);
        member.setStudentEmail(studentEmail);
        member.setUser(user);

        return memberRepository.save(member);
    }

    @Transactional
    public Set<DistributionGroupMember> addMembersInBulk(Long groupId, List<String> emails) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));

        Set<DistributionGroupMember> addedMembers = new HashSet<>();
        for (String email : emails) {
            try {
                User user = userRepository.findBySchoolEmail(email).orElse(null);
                
                boolean alreadyExists = memberRepository.existsByGroupAndStudentEmail(group, email);
                if (alreadyExists) {
                    continue;
                }

                DistributionGroupMember member = new DistributionGroupMember();
                member.setGroup(group);
                member.setStudentEmail(email);
                member.setUser(user);
                
                DistributionGroupMember saved = memberRepository.save(member);
                addedMembers.add(saved);
            } catch (Exception e) {
                System.err.println("Failed to add member " + email + ": " + e.getMessage());
            }
        }
        
        return addedMembers;
    }

    @Transactional
    public void removeMember(Long groupId, Long memberId) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));

        DistributionGroupMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (!member.getGroup().getId().equals(groupId)) {
            throw new RuntimeException("Member does not belong to this group");
        }

        memberRepository.delete(member);
    }

    public List<String> getGroupMemberEmails(Long groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .map(DistributionGroupMember::getStudentEmail)
                .collect(Collectors.toList());
    }

    public Set<DistributionGroupMember> getGroupMembers(Long groupId) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));
        return group.getMembers();
    }

    public int getMemberCount(Long groupId) {
        DistributionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Distribution group not found"));
        return group.getMembers().size();
    }

    public boolean isMemberOfGroup(Long groupId, String email) {
        DistributionGroup group = groupRepository.findById(groupId).orElse(null);
        if (group == null) return false;
        return memberRepository.existsByGroupAndStudentEmail(group, email);
    }
}
