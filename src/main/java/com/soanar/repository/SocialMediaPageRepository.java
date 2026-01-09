package com.soanar.repository;

import com.soanar.model.SocialMediaPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialMediaPageRepository extends JpaRepository<SocialMediaPage, Long> {
}
