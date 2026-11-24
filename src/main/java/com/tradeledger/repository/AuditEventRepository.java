package com.tradeledger.repository;

import com.tradeledger.domain.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {

    List<AuditEventDocument> findByPortfolioIdOrderByTimestampDesc(String portfolioId);

    List<AuditEventDocument> findByPortfolioIdAndEventTypeOrderByTimestampDesc(
            String portfolioId, String eventType);

    List<AuditEventDocument> findByPortfolioIdAndTimestampBetweenOrderByTimestampDesc(
            String portfolioId, Instant from, Instant to);

    List<AuditEventDocument> findByPortfolioIdAndEventTypeAndTimestampBetweenOrderByTimestampDesc(
            String portfolioId, String eventType, Instant from, Instant to);
}

