package com.tradeledger.service;

import com.tradeledger.domain.model.AuditEventDocument;
import com.tradeledger.dto.response.AuditEventResponse;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.AuditEventRepository;
import com.tradeledger.repository.PortfolioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final PortfolioRepository portfolioRepository;
    private final MongoTemplate mongoTemplate;

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            PortfolioRepository portfolioRepository,
            MongoTemplate mongoTemplate) {
        this.auditEventRepository = auditEventRepository;
        this.portfolioRepository = portfolioRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<AuditEventResponse> getAuditEvents(UUID portfolioId, String type, Instant from, Instant to) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("portfolioId").is(portfolioId.toString()));

        if (type != null && !type.trim().isEmpty()) {
            criteriaList.add(Criteria.where("eventType").is(type.trim().toUpperCase()));
        }

        if (from != null && to != null) {
            criteriaList.add(Criteria.where("timestamp").gte(from).lte(to));
        } else if (from != null) {
            criteriaList.add(Criteria.where("timestamp").gte(from));
        } else if (to != null) {
            criteriaList.add(Criteria.where("timestamp").lte(to));
        }

        query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));

        List<AuditEventDocument> documents = mongoTemplate.find(query, AuditEventDocument.class);

        return documents.stream()
                .map(doc -> new AuditEventResponse(
                        doc.getId(),
                        doc.getEventType(),
                        doc.getPortfolioId(),
                        doc.getPayload(),
                        doc.getTimestamp()
                ))
                .toList();
    }
}
