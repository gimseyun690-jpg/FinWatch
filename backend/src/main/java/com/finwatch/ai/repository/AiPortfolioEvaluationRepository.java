package com.finwatch.ai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.ai.domain.AiPortfolioEvaluation;

public interface AiPortfolioEvaluationRepository extends JpaRepository<AiPortfolioEvaluation, Long> {

    Optional<AiPortfolioEvaluation> findByCacheKey(String cacheKey);
}
