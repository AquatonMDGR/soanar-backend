package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.AnnouncementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final RecipientResolverService recipientResolverService;
    
    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.service-role-key}")
    private String supabaseKey;

    @Value("${media.public-base-url:}")
    private String mediaPublicBaseUrl;

    @Value("${media.origin-base-url:}")
    private String mediaOriginBaseUrl;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                                NotificationService notificationService,
                                RecipientResolverService recipientResolverService) {
        this.announcementRepository = announcementRepository;
        this.notificationService = notificationService;
        this.recipientResolverService = recipientResolverService;
    }

    public List<Announcement> listAll() {
        return announcementRepository.findAllActiveOrderByCreatedAtDesc();
    }

    public List<Announcement> getPublished() {
        return announcementRepository.findByStatusIn(java.util.Arrays.asList("PUBLISHED", "APPROVED"));
    }

    public List<Announcement> getPublishedForUser(User user) {
        Instant schoolYearStart = getCurrentSchoolYearStartInstant();
        return getPublished().stream()
                .filter(announcement -> announcement.getCreatedAt() != null && !announcement.getCreatedAt().isBefore(schoolYearStart))
                .filter(announcement -> recipientResolverService.isUserInAudience(user, announcement))
                .collect(Collectors.toList());
    }

    public List<Announcement> getPending() {
        return announcementRepository.findByStatus("PENDING");
    }

    @Transactional
    public Announcement create(Announcement a, User poster) {
        // Set the poster - Spring Data JPA will manage the relationship
        a.setPostedBy(poster);
        String posterRole = poster.getRole();
        a.setPosterRoleSnapshot(posterRole);
        a.setPosterNameSnapshot(poster.getName());
        a.setPosterPhotoSnapshot(poster.getPhotoUrl());
        
        if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
            a.setStatus("PUBLISHED");
            a.setPublishedAt(Instant.now());
        } else {
            a.setStatus("PENDING");
        }
        
        a.setCreatedAt(Instant.now());
        
        try {
            Announcement saved = announcementRepository.save(a);
            announcementRepository.flush();  // Ensure data is flushed to database
            
            // DEBUG: Log the saved announcement details
            System.out.println("DEBUG: Announcement saved and flushed with ID: " + saved.getId());
            System.out.println("DEBUG: imageUrl: " + saved.getImageUrl());
            System.out.println("DEBUG: imageUrls size: " + (saved.getImageUrls() != null ? saved.getImageUrls().size() : "NULL"));
            if (saved.getImageUrls() != null) {
                for (int i = 0; i < saved.getImageUrls().size(); i++) {
                    System.out.println("DEBUG: imageUrls[" + i + "]: " + saved.getImageUrls().get(i));
                }
            }
            
            schedulePostCreateNotifications(saved.getId(), posterRole);
            
            return saved;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create announcement: " + e.getMessage(), e);
        }
    }

    private void schedulePostCreateNotifications(Long announcementId, String posterRole) {
        if (announcementId == null) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> dispatchPostCreateNotifications(announcementId, posterRole));
                }
            });
            return;
        }

        CompletableFuture.runAsync(() -> dispatchPostCreateNotifications(announcementId, posterRole));
    }

    private void dispatchPostCreateNotifications(Long announcementId, String posterRole) {
        try {
            Announcement persisted = announcementRepository.findById(announcementId).orElse(null);
            if (persisted == null) {
                System.err.println("Warning: Announcement not found for notification dispatch: " + announcementId);
                return;
            }

            if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
                if (Boolean.TRUE.equals(persisted.getIsEmergency())) {
                    notificationService.notifyAllStudentsEmergency(persisted);
                } else {
                    notificationService.notifyStudentsOfPublishedAnnouncement(persisted);
                }
                notificationService.notifyEventCreator(persisted);
            } else if ("Student Organization".equals(posterRole)) {
                notificationService.notifyOSASOfNewAnnouncement(persisted);
                notificationService.notifyEventCreator(persisted);
            }
        } catch (Exception notifyError) {
            System.err.println("Warning: Failed to send notifications for announcement " + announcementId + ": " + notifyError.getMessage());
            notifyError.printStackTrace();
        }
    }

    public Optional<Announcement> findById(Long id) {
        return announcementRepository.findActiveById(id);
    }

    @Transactional
    public Announcement approve(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("PUBLISHED");
        a.setPublishedAt(Instant.now());
        return announcementRepository.save(a);
    }

    @Transactional
    public Announcement approve(Long id, User approver, String notes) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("PUBLISHED");
        a.setPublishedAt(Instant.now());
        a.setApprovedBy(approver);
        a.setApprovedAt(Instant.now());
        a.setApprovalNotes(notes);
        return announcementRepository.save(a);
    }

    @Transactional
    public Announcement reject(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("REJECTED");
        return announcementRepository.save(a);
    }

    @Transactional
    public Announcement reject(Long id, User rejector, String rejectionReason) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("REJECTED");
        a.setApprovedBy(rejector);
        a.setApprovedAt(Instant.now());
        a.setApprovalNotes(rejectionReason);
        return announcementRepository.save(a);
    }
    
    @Transactional
    public void delete(Long id) {
        announcementRepository.deleteById(id);
    }
    
    @Transactional
    public void notifyAfterApproval(Long id, boolean approved) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        
        // DEBUG: Check imageUrls after fetch from DB
        System.out.println("DEBUG notifyAfterApproval: imageUrl = " + a.getImageUrl());
        System.out.println("DEBUG notifyAfterApproval: imageUrls size = " + (a.getImageUrls() != null ? a.getImageUrls().size() : "NULL"));
        if (a.getImageUrls() != null) {
            for (int i = 0; i < a.getImageUrls().size(); i++) {
                System.out.println("DEBUG notifyAfterApproval: imageUrls[" + i + "] = " + a.getImageUrls().get(i));
            }
        }
        
        try {
            if (a.getPostedBy() != null) {
                // Notify the poster about approval/rejection
                notificationService.notifyApprovalResult(a, approved);
                
                // If approved, send emails to distribution group members (Student Org announcements)
                if (approved) {
                    String posterRole = a.getPostedBy().getRole();
                    if ("Student Organization".equals(posterRole)) {
                        // Student Org announcement approved -> email distribution group members
                        notificationService.notifyDistributionGroupMembers(a);
                    } else {
                        // For other roles (shouldn't happen), email all students
                        notificationService.notifyStudentsOfPublishedAnnouncement(a);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send notifications for " + (approved ? "approval" : "rejection") + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public String uploadToSupabase(MultipartFile file, String bucketName, String folderPath) throws IOException {
        try {
            // Normalize and validate configuration
            String resolvedUrl = supabaseUrl != null ? supabaseUrl.trim() : "";
            String resolvedKey = supabaseKey != null ? supabaseKey.trim() : "";
            if (resolvedUrl.isEmpty() || "your-service-role-key-here".equals(resolvedUrl)) {
                throw new IOException("Supabase URL is not configured. Please set SUPABASE_URL in .env file.");
            }
            if (resolvedKey.isEmpty() || "your-service-role-key-here".equals(resolvedKey)) {
                throw new IOException("Supabase service role key is not configured. Please set SUPABASE_SERVICE_ROLE_KEY in .env file.");
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : "";
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            String fullPath = folderPath + "/" + uniqueFilename;
            
            // Build Supabase Storage API URL
            String uploadUrl = resolvedUrl + "/storage/v1/object/" + bucketName + "/" + fullPath;
            
            System.out.println("Uploading to Supabase Storage: " + uploadUrl);
            
            // Create HTTP request
            HttpClient client = HttpClient.newHttpClient();
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + resolvedKey)
                .header("apikey", resolvedKey)
                .header("Content-Type", contentType)
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();
            
            // Send request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("Supabase response status: " + response.statusCode());
            System.out.println("Supabase response body: " + response.body());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Return public URL
                String publicUrl = resolvedUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                System.out.println("Upload successful, public URL: " + publicUrl);
                return normalizePublicMediaUrl(publicUrl, resolvedUrl);
            } else {
                throw new IOException("Supabase upload failed: " + response.statusCode() + " - " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        }
    }
    
    public List<String> uploadMultipleToSupabase(MultipartFile[] files, String bucketName, String folderPath) {
        List<String> urls = new ArrayList<>();
        
        if (files == null || files.length == 0) {
            return urls;
        }
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                try {
                    String url = uploadToSupabase(file, bucketName, folderPath);
                    urls.add(url);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to upload file " + file.getOriginalFilename() + ": " + e.getMessage());
                    // Continue with next file instead of failing completely
                }
            }
        }
        
        return urls;
    }

    private String normalizePublicMediaUrl(String publicUrl, String resolvedSupabaseUrl) {
        String publicBase = normalizeBase(mediaPublicBaseUrl);
        if (publicBase.isEmpty()) {
            return publicUrl;
        }

        String originBase = normalizeBase(mediaOriginBaseUrl);
        if (originBase.isEmpty()) {
            originBase = normalizeBase(resolvedSupabaseUrl + "/storage/v1/object/public");
        }

        if (publicUrl.startsWith(originBase)) {
            return publicBase + publicUrl.substring(originBase.length());
        }

        return publicUrl;
    }

    private String normalizeBase(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Instant getCurrentSchoolYearStartInstant() {
        LocalDate now = LocalDate.now();
        int schoolYearStartYear = now.getMonthValue() >= 6 ? now.getYear() : now.getYear() - 1;
        LocalDate startDate = LocalDate.of(schoolYearStartYear, 6, 1);
        return startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
