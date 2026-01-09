package com.soanar.service;

import com.soanar.model.SocialMediaPage;
import com.soanar.model.SocialMediaToken;
import com.soanar.model.CrossPostedAnnouncement;
import com.soanar.model.Announcement;
import com.soanar.repository.SocialMediaPageRepository;
import com.soanar.repository.SocialMediaTokenRepository;
import com.soanar.repository.CrossPostedAnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SocialMediaService {

    private final SocialMediaPageRepository pageRepository;
    private final SocialMediaTokenRepository tokenRepository;
    private final CrossPostedAnnouncementRepository crossPostedRepository;

    public SocialMediaService(SocialMediaPageRepository pageRepository,
                               SocialMediaTokenRepository tokenRepository,
                               CrossPostedAnnouncementRepository crossPostedRepository) {
        this.pageRepository = pageRepository;
        this.tokenRepository = tokenRepository;
        this.crossPostedRepository = crossPostedRepository;
    }

    public List<SocialMediaPage> getAllPages() {
        return pageRepository.findAll();
    }

    @Transactional
    public SocialMediaPage connectPage(String platform, String pageName, String accessToken, Instant expiresAt) {
        SocialMediaPage page = new SocialMediaPage();
        page.setPlatform(platform);
        page.setPageName(pageName);
        page = pageRepository.save(page);

        SocialMediaToken token = new SocialMediaToken();
        token.setPage(page);
        token.setAccessToken(accessToken);
        token.setExpiresAt(expiresAt);
        tokenRepository.save(token);

        return page;
    }

    @Transactional
    public void postToPage(Long pageId, Announcement announcement) {
        SocialMediaPage page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found"));

        CrossPostedAnnouncement crossPost = new CrossPostedAnnouncement();
        crossPost.setAnnouncement(announcement);
        crossPost.setPage(page);
        crossPost.setPostedAt(Instant.now());
        crossPost.setStatus("SUCCESS");
        crossPostedRepository.save(crossPost);
    }
}
