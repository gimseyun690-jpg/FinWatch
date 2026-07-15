package com.finwatch.ai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.ai.domain.AiTechnicalExplanation;

public interface AiTechnicalExplanationRepository extends JpaRepository<AiTechnicalExplanation, Long> {

    Optional<AiTechnicalExplanation> findByCacheKey(String cacheKey);
}
