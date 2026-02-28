package com.soanar.controller;

import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.model.User;
import com.soanar.service.OrganizationSettingsService;
import com.soanar.service.DistributionGroupService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/distribution-groups")
@CrossOrigin(origins = "*")
public class DistributionGroupController {

    private final DistributionGroupService distributionGroupService;
    private final UserService userService;
    private final OrganizationSettingsService organizationSettingsService;
    private final JwtUtil jwtUtil;

    public DistributionGroupController(DistributionGroupService distributionGroupService,
                                       UserService userService,
                                       OrganizationSettingsService organizationSettingsService,
                                       JwtUtil jwtUtil) {
        this.distributionGroupService = distributionGroupService;
        this.userService = userService;
        this.organizationSettingsService = organizationSettingsService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Get all distribution groups for an organization
     */
    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<?> getGroupsByOrganization(
            @PathVariable String organizationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            List<DistributionGroup> groups = distributionGroupService.getGroupsByOrganization(organizationId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Create a new distribution group
     */
    @PostMapping
    public ResponseEntity<?> createGroup(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String organizationId = (String) payload.get("organizationId");
            String name = (String) payload.get("name");
            String description = (String) payload.get("description");

            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
            }

            if (organizationId == null || organizationId.isBlank()) {
                organizationId = organizationSettingsService.resolveDefaultOrganizationId();
            }

            User creator = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for token email"));

            DistributionGroup group = distributionGroupService.createGroup(organizationId, name, description, creator);
            return ResponseEntity.ok(group);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update distribution group
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<?> updateGroup(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);

            String name = (String) payload.get("name");
            String description = (String) payload.get("description");

            DistributionGroup group = distributionGroupService.updateGroup(groupId, name, description);
            return ResponseEntity.ok(group);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a distribution group
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            distributionGroupService.deleteGroup(groupId);
            return ResponseEntity.ok(Map.of("message", "Group deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add a member to a distribution group
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> addMember(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> payload) {
        try {
            String studentEmail = payload.get("studentEmail");
            if (studentEmail == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "studentEmail is required"));
            }

            DistributionGroupMember member = distributionGroupService.addMember(groupId, studentEmail);
            return ResponseEntity.ok(member);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add multiple members to a distribution group (bulk operation)
     */
    @PostMapping("/{groupId}/members/bulk")
    public ResponseEntity<?> addMembersInBulk(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<String> emails = (List<String>) payload.get("emails");
            if (emails == null || emails.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "emails list is required"));
            }

            Set<DistributionGroupMember> members = distributionGroupService.addMembersInBulk(groupId, emails);
            return ResponseEntity.ok(Map.of("count", members.size(), "members", members));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get members of a distribution group
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<?> getMembers(
            @PathVariable Long groupId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Set<DistributionGroupMember> members = distributionGroupService.getGroupMembers(groupId);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove a member from a distribution group
     */
    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<?> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long memberId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            distributionGroupService.removeMember(groupId, memberId);
            return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get total member count for a group
     */
    @GetMapping("/{groupId}/member-count")
    public ResponseEntity<?> getMemberCount(@PathVariable Long groupId) {
        try {
            int count = distributionGroupService.getMemberCount(groupId);
            return ResponseEntity.ok(Map.of("groupId", groupId, "memberCount", count));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
