package com.soanar.controller;

import com.soanar.model.Announcement;
import com.soanar.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Controller
@RequestMapping("/share")
public class ShareController {

    @Autowired
    private AnnouncementService announcementService;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @GetMapping("/announcement/{id}")
    public void shareAnnouncement(@PathVariable Long id, HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        
        try {
            Announcement announcement = announcementService.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
            
            // Only allow sharing PUBLISHED or APPROVED announcements
            if (!"PUBLISHED".equals(announcement.getStatus()) && 
                !"APPROVED".equals(announcement.getStatus())) {
                response.sendError(404, "Announcement not found");
                return;
            }
            
            String title = announcement.getTitle() != null ? announcement.getTitle() : "Announcement";
            String description = announcement.getDescription() != null ? announcement.getDescription() : "";
            String imageUrl = "";
            if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
                imageUrl = announcement.getImageUrls().get(0);
            } else if (announcement.getImageUrl() != null) {
                imageUrl = announcement.getImageUrl();
            }
            
            String resolvedFrontendUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:3000";
            if (resolvedFrontendUrl.endsWith("/")) {
                resolvedFrontendUrl = resolvedFrontendUrl.substring(0, resolvedFrontendUrl.length() - 1);
            }
            String announcementUrl = resolvedFrontendUrl + "/announcement/" + id;
            
            PrintWriter out = response.getWriter();
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\">");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("  <title>" + escapeHtml(title) + "</title>");
            out.println("  <meta name=\"description\" content=\"" + escapeHtml(description) + "\">");
            
            // Open Graph tags for Facebook, LinkedIn
            out.println("  <meta property=\"og:title\" content=\"" + escapeHtml(title) + "\" />");
            out.println("  <meta property=\"og:description\" content=\"" + escapeHtml(description) + "\" />");
            out.println("  <meta property=\"og:type\" content=\"article\" />");
            out.println("  <meta property=\"og:url\" content=\"" + escapeHtml(announcementUrl) + "\" />");
            if (!imageUrl.isEmpty()) {
                out.println("  <meta property=\"og:image\" content=\"" + escapeHtml(imageUrl) + "\" />");
                out.println("  <meta property=\"og:image:secure_url\" content=\"" + escapeHtml(imageUrl) + "\" />");
            }
            
            // Twitter Card tags
            out.println("  <meta name=\"twitter:card\" content=\"summary_large_image\" />");
            out.println("  <meta name=\"twitter:title\" content=\"" + escapeHtml(title) + "\" />");
            out.println("  <meta name=\"twitter:description\" content=\"" + escapeHtml(description) + "\" />");
            if (!imageUrl.isEmpty()) {
                out.println("  <meta name=\"twitter:image\" content=\"" + escapeHtml(imageUrl) + "\" />");
            }
            
            // JavaScript redirect (browsers only, not bots)
            out.println("  <script type=\"text/javascript\">");
            out.println("    window.location.href = '" + announcementUrl + "';");
            out.println("  </script>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <h1>" + escapeHtml(title) + "</h1>");
            if (!description.isEmpty()) {
                out.println("  <p>" + escapeHtml(description) + "</p>");
            }
            if (!imageUrl.isEmpty()) {
                out.println("  <img src=\"" + escapeHtml(imageUrl) + "\" alt=\"" + escapeHtml(title) + "\" style=\"max-width:100%;height:auto;\" />");
            }
            out.println("  <p>If you are not redirected, <a href=\"" + announcementUrl + "\">click here</a>.</p>");
            out.println("</body>");
            out.println("</html>");
            
        } catch (Exception e) {
            response.sendError(404, "Announcement not found");
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;")
                   .replace("\n", " ")
                   .replace("\r", " ")
                   .replace("\t", " ")
                   .replaceAll("\\s+", " ")  // Replace multiple spaces with single space
                   .trim();
    }
}
