package com.soanar.model;

import jakarta.persistence.*;

@Entity
@Table(name = "distribution_group_members")
public class DistributionGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private DistributionGroup group;

    @Column(name = "student_email", nullable = false)
    private String studentEmail;

    public DistributionGroupMember() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DistributionGroup getGroup() { return group; }
    public void setGroup(DistributionGroup group) { this.group = group; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
}
