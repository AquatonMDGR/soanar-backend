package com.soanar.repository;

import com.soanar.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Remove findByEmail for now
}
