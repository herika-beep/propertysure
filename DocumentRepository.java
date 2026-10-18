
package com.pbl.propertyauth.repository;

import com.pbl.propertyauth.model.PropertyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository
        extends JpaRepository<PropertyDocument, Long> {

    List<PropertyDocument> findByOwnerUserIdOrderByUploadedAtDesc(Long ownerUserId);

    List<PropertyDocument> findByOwnerUserIdAndOriginalFileNameContainingIgnoreCaseOrderByUploadedAtDesc(
            Long ownerUserId, String originalFileName);
}
