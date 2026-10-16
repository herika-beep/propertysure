package com.pbl.propertyauth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "property_documents")
public class PropertyDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerUserId;
    private String originalFileName;
    private String sha256;
    private String status;
    private String extractedOwnerName;
    private String propertyId;
    private String registrationNumber;
    @Column(length = 2000)
    private String propertyAddress;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long id) { this.ownerUserId = id; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String name) { this.originalFileName = name; }
    public String getSha256() { return sha256; }
    public void setSha256(String hash) { this.sha256 = hash; }
    public String getExtractedOwnerName() { return extractedOwnerName; }
    public void setExtractedOwnerName(String v) { this.extractedOwnerName = v; }
    public String getPropertyId() { return propertyId; }
    public void setPropertyId(String v) { this.propertyId = v; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String v) { this.propertyAddress = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String text) { this.extractedText = text; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}