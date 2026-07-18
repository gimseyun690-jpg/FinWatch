package com.finwatch.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.ai.domain.AiAnalysis;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    boolean existsByNewsId(Long newsId);

    Optional<AiAnalysis> findByNewsIdAndFeatureTypeAndPromptVersionAndContentHash(
            Long newsId, String featureType, String promptVersion, String contentHash);

    List<AiAnalysis> findAllByNewsIdAndContentHash(Long newsId, String contentHash);

    List<AiAnalysis> findAllByNewsIdInAndFeatureTypeOrderByGeneratedAtDescIdDesc(
            List<Long> newsIds,
            String featureType);
}
