package com.pbl.propertyauth.controller;

import com.pbl.propertyauth.model.PropertyDocument;
import com.pbl.propertyauth.repository.DocumentRepository;
import com.pbl.propertyauth.service.DocumentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentRepository documents;
    private final DocumentService service;

    public DocumentController(
            DocumentRepository documents, DocumentService service) {
        this.documents = documents;
        this.service = service;
    }

    private Long currentUser(HttpSession session) {
        Object id = session.getAttribute("userId");
        return id instanceof Long ? (Long) id : null;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        Long userId = currentUser(session);

        if (userId == null) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Please log in first."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Select a file."));
        }

        if (file.getSize() > 15L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Maximum file size is 15 MB."));
        }

        try {
            String text = service.extractText(file);
            Map<String, String> fields = service.extractFields(text);
            List<String> findings = service.analyze(file, text, fields);

            PropertyDocument doc = new PropertyDocument();
            doc.setOwnerUserId(userId);
            doc.setOriginalFileName(
                Objects.toString(file.getOriginalFilename(), "document"));
            doc.setSha256(service.sha256(file.getBytes()));
            doc.setExtractedText(text);
            doc.setFindings(String.join("\n", findings));
            doc.setExtractedOwnerName(fields.get("ownerName"));
            doc.setPropertyId(fields.get("propertyId"));
            doc.setRegistrationNumber(fields.get("registrationNumber"));
            doc.setPropertyAddress(fields.get("address"));
            doc.setStatus(service.resultStatus(findings, fields));

            return ResponseEntity.ok(toMap(documents.save(doc)));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
    ex.printStackTrace();

    return ResponseEntity.internalServerError().body(
        Map.of("message",
            "Document processing failed. Check OCR setup and file format."));}
}
    

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "") String q,
            HttpSession session) {

        Long userId = currentUser(session);

        if (userId == null) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Please log in."));
        }

        List<PropertyDocument> results = q.isBlank()
            ? documents.findByOwnerUserIdOrderByUploadedAtDesc(userId)
            : documents
                .findByOwnerUserIdAndOriginalFileNameContainingIgnoreCaseOrderByUploadedAtDesc(
                    userId, q);

        return ResponseEntity.ok(
            results.stream().map(this::toMap)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @PathVariable Long id, HttpSession session) {

        Long userId = currentUser(session);

        if (userId == null) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Please log in."));
        }

        return documents.findById(id)
            .filter(doc -> doc.getOwnerUserId().equals(userId))
            .<ResponseEntity<?>>map(
                doc -> ResponseEntity.ok(toMap(doc)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toMap(PropertyDocument doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("originalFileName", doc.getOriginalFileName());
        map.put("sha256", doc.getSha256());
        map.put("status", doc.getStatus());
        map.put("ownerName", doc.getExtractedOwnerName());
        map.put("propertyId", doc.getPropertyId());
        map.put("registrationNumber", doc.getRegistrationNumber());
        map.put("address", doc.getPropertyAddress());
        map.put("findings", doc.getFindings());
        map.put("extractedText", doc.getExtractedText());
        map.put("uploadedAt", doc.getUploadedAt());
        return map;
    }
}
