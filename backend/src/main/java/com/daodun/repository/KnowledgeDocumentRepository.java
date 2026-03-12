package com.daodun.repository;

import com.daodun.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findBySourcePath(String sourcePath);

    Optional<KnowledgeDocument> findByPositionName(String positionName);
}
