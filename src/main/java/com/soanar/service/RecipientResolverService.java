package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.model.User;
import com.soanar.repository.DistributionGroupMemberRepository;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RecipientResolverService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final DistributionGroupMemberRepository distributionGroupMemberRepository;

    public RecipientResolverService(UserRepository userRepository,
                                    DistributionGroupMemberRepository distributionGroupMemberRepository) {
        this.userRepository = userRepository;
        this.distributionGroupMemberRepository = distributionGroupMemberRepository;
    }

    public Set<String> resolveRecipients(Announcement announcement) {
        Set<String> recipients = new LinkedHashSet<>();

        List<String> yearLevels = announcement.getTargetYearLevels();
        List<String> schools = announcement.getTargetSchools();
        List<String> manualEmails = announcement.getTargetManualEmails();

        boolean hasYear = yearLevels != null && !yearLevels.isEmpty();
        boolean hasSchool = schools != null && !schools.isEmpty();
        boolean hasGroups = announcement.getDistributionGroups() != null && !announcement.getDistributionGroups().isEmpty();
        boolean hasManual = manualEmails != null && !manualEmails.isEmpty();

        if (hasYear && hasSchool) {
            for (User user : userRepository.findByRoleAndYearLevelInAndSchoolIn("Student", yearLevels, schools)) {
                addIfValid(recipients, user.getSchoolEmail());
            }
        } else {
            if (hasYear) {
                for (User user : userRepository.findByRoleAndYearLevelIn("Student", yearLevels)) {
                    addIfValid(recipients, user.getSchoolEmail());
                }
            }
            if (hasSchool) {
                for (User user : userRepository.findByRoleAndSchoolIn("Student", schools)) {
                    addIfValid(recipients, user.getSchoolEmail());
                }
            }
        }

        if (hasGroups) {
            for (DistributionGroup group : announcement.getDistributionGroups()) {
                List<DistributionGroupMember> members = distributionGroupMemberRepository.findByGroupId(group.getId());
                for (DistributionGroupMember member : members) {
                    addIfValid(recipients, member.getStudentEmail());
                }
            }
        }

        if (hasManual) {
            for (String email : manualEmails) {
                addIfValid(recipients, email);
            }
        }

        return recipients;
    }

    public Set<String> resolveWithFallback(Announcement announcement) {
        Set<String> resolved = resolveRecipients(announcement);
        if (!resolved.isEmpty()) {
            return resolved;
        }

        String posterRole = announcement.getPostedBy() != null ? announcement.getPostedBy().getRole() : null;
        if ("Student Organization".equals(posterRole)) {
            if (announcement.getDistributionGroups() != null) {
                for (DistributionGroup group : announcement.getDistributionGroups()) {
                    List<DistributionGroupMember> members = distributionGroupMemberRepository.findByGroupId(group.getId());
                    for (DistributionGroupMember member : members) {
                        addIfValid(resolved, member.getStudentEmail());
                    }
                }
            }
            return resolved;
        }

        for (User user : userRepository.findByRole("Student")) {
            addIfValid(resolved, user.getSchoolEmail());
        }

        return resolved;
    }

    private void addIfValid(Set<String> emails, String email) {
        if (email == null) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.isEmpty() && EMAIL_PATTERN.matcher(normalized).matches()) {
            emails.add(normalized);
        }
    }
}
