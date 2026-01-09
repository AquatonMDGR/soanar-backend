package com.soanar.repository;

import com.soanar.model.SocialMediaToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialMediaTokenRepository extends JpaRepository<SocialMediaToken, Long> {
}
