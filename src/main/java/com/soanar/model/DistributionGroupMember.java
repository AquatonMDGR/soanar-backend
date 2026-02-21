package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "distribution_group_members")
public class DistributionGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private DistributionGroup group;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "student_email", nullable = false)
    private String studentEmail;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    public DistributionGroupMember() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DistributionGroup getGroup() { return group; }
    public void setGroup(DistributionGroup group) { this.group = group; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
