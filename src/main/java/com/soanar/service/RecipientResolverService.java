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
import java.util.ArrayList;
import java.util.Collections;
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
        List<String> normalizedYearLevels = expandYearLevelAliases(yearLevels);
        List<String> schools = announcement.getTargetSchools();
        List<String> manualEmails = announcement.getTargetManualEmails();
        Set<DistributionGroup> groups = safeDistributionGroups(announcement);

        boolean hasYear = normalizedYearLevels != null && !normalizedYearLevels.isEmpty();
        boolean hasSchool = schools != null && !schools.isEmpty();
        boolean hasGroups = groups != null && !groups.isEmpty();
        boolean hasManual = manualEmails != null && !manualEmails.isEmpty();

        if (hasYear && hasSchool) {
            for (User user : userRepository.findByRoleAndYearLevelInAndSchoolIn("Student", normalizedYearLevels, schools)) {
                addIfValid(recipients, user.getSchoolEmail());
            }
        } else {
            if (hasYear) {
                for (User user : userRepository.findByRoleAndYearLevelIn("Student", normalizedYearLevels)) {
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
            for (DistributionGroup group : groups) {
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

        if (shouldFallbackToAllStudents(announcement)) {
            for (User user : userRepository.findByRole("Student")) {
                addIfValid(resolved, user.getSchoolEmail());
            }
            return resolved;
        }

        Set<DistributionGroup> groups = safeDistributionGroups(announcement);
        if (groups != null) {
            for (DistributionGroup group : groups) {
                List<DistributionGroupMember> members = distributionGroupMemberRepository.findByGroupId(group.getId());
                for (DistributionGroupMember member : members) {
                    addIfValid(resolved, member.getStudentEmail());
                }
            }
        }

        return resolved;
    }

    private boolean shouldFallbackToAllStudents(Announcement announcement) {
        String posterRole = announcement.getPostedBy() != null ? announcement.getPostedBy().getRole() : null;
        if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
            return true;
        }

        List<String> normalizedYearLevels = expandYearLevelAliases(announcement.getTargetYearLevels());
        List<String> schools = announcement.getTargetSchools();
        List<String> manualEmails = announcement.getTargetManualEmails();
        Set<DistributionGroup> groups = safeDistributionGroups(announcement);

        boolean hasYear = normalizedYearLevels != null && !normalizedYearLevels.isEmpty();
        boolean hasSchool = schools != null && !schools.isEmpty();
        boolean hasManual = manualEmails != null && !manualEmails.isEmpty();
        boolean hasGroups = groups != null && !groups.isEmpty();

        return !hasYear && !hasSchool && !hasManual && !hasGroups;
    }

    private Set<DistributionGroup> safeDistributionGroups(Announcement announcement) {
        try {
            Set<DistributionGroup> groups = announcement.getDistributionGroups();
            return groups != null ? groups : Collections.emptySet();
        } catch (Exception ex) {
            Long announcementId = announcement != null ? announcement.getId() : null;
            System.err.println("Warning: unable to resolve distribution groups for announcement " + announcementId + ": " + ex.getMessage());
            return Collections.emptySet();
        }
    }

    public boolean isUserInAudience(User user, Announcement announcement) {
        if (user == null || announcement == null) {
            return false;
        }

        String userEmail = normalizeEmail(user.getSchoolEmail());
        List<String> yearLevels = announcement.getTargetYearLevels();
        List<String> normalizedYearLevels = expandYearLevelAliases(yearLevels);
        List<String> schools = announcement.getTargetSchools();
        List<String> manualEmails = announcement.getTargetManualEmails();

        boolean hasYear = normalizedYearLevels != null && !normalizedYearLevels.isEmpty();
        boolean hasSchool = schools != null && !schools.isEmpty();
        boolean hasManual = manualEmails != null && !manualEmails.isEmpty();

        if (!hasYear && !hasSchool && !hasManual) {
            return true;
        }

        boolean yearSchoolMatch = false;
        if (hasYear || hasSchool) {
            boolean yearMatch = !hasYear || valueInList(user.getYearLevel(), normalizedYearLevels);
            boolean schoolMatch = !hasSchool || valueInList(user.getSchool(), schools);
            yearSchoolMatch = yearMatch && schoolMatch;
        }

        boolean manualMatch = hasManual && manualEmails.stream()
                .map(this::normalizeEmail)
                .anyMatch(email -> email.equals(userEmail));

        return yearSchoolMatch || manualMatch;
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private boolean valueInList(String value, List<String> allowedValues) {
        if (value == null || allowedValues == null) {
            return false;
        }
        String normalizedValue = value.trim();
        return allowedValues.stream().anyMatch(item -> item != null && normalizedValue.equalsIgnoreCase(item.trim()));
    }

    private List<String> expandYearLevelAliases(List<String> yearLevels) {
        Set<String> expanded = new LinkedHashSet<>();
        if (yearLevels == null) {
            return List.of();
        }

        for (String yearLevel : yearLevels) {
            String normalized = normalizeYearLevelBase(yearLevel);
            if (normalized.isEmpty()) {
                continue;
            }
            expanded.add(normalized);
            expanded.add(normalized + " Year");
        }

        return new ArrayList<>(expanded);
    }

    private String normalizeYearLevelBase(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("(?i)\\s*year$", "").trim();
    }
}
