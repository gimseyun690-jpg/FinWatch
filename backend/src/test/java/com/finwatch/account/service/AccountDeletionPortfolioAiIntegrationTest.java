package com.finwatch.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.finwatch.account.dto.AccountDeletionRequest;
import com.finwatch.ai.domain.AiPortfolioEvaluation;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.repository.AiPortfolioEvaluationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.auth.session.AuthenticatedSession;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.domain.UserStatus;
import com.finwatch.user.identity.AuthProvider;
import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest
@ActiveProfiles("demo")
class AccountDeletionPortfolioAiIntegrationTest {

    @Autowired AccountDeletionService deletionService;
    @Autowired AppUserRepository users;
    @Autowired AiPortfolioEvaluationRepository evaluations;
    @Autowired AiUsageLogRepository usageLogs;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void deletesPortfolioEvaluationAndAnonymizesItsUsageLog() {
        AppUser user = users.save(AppUser.createSocial("삭제 검증 사용자", null));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String requestId = UUID.randomUUID().toString();
        AiPortfolioEvaluation evaluation = evaluations.save(AiPortfolioEvaluation.create(
                user,
                now,
                now.truncatedTo(ChronoUnit.MINUTES),
                "a".repeat(64),
                "b".repeat(64),
                "portfolio-evaluation-v1",
                "DIVERSIFIED",
                "분산 상태",
                "포트폴리오 구성 설명",
                "{}",
                "{}",
                "{}",
                "{}",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "test-model",
                10,
                5,
                BigDecimal.ZERO.setScale(8),
                "test:portfolio-deletion:" + UUID.randomUUID(),
                now));
        usageLogs.save(AiUsageLog.portfolioEvaluationSuccess(
                requestId,
                evaluation,
                "test-model",
                user.getId(),
                10,
                5,
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                false,
                1,
                "portfolio-evaluation-v1"));
        jdbcTemplate.update(
                "UPDATE ai_usage_logs SET user_id = ? WHERE request_id = ?",
                user.getId(),
                requestId);

        deletionService.delete(
                new AuthenticatedSession(
                        "account-deletion-test",
                        user,
                        AuthProvider.KAKAO,
                        now,
                        now.plus(30, ChronoUnit.MINUTES)),
                new AccountDeletionRequest("DELETE", null));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_portfolio_evaluations WHERE user_id = ?",
                Integer.class,
                user.getId())).isZero();
        Map<String, Object> usage = jdbcTemplate.queryForMap("""
                SELECT user_id AS "userId",
                       target_id AS "targetId",
                       portfolio_evaluation_id AS "portfolioEvaluationId"
                FROM ai_usage_logs
                WHERE request_id = ?
                """, requestId);
        assertThat(usage.get("userId")).isNull();
        assertThat(((Number) usage.get("targetId")).longValue()).isZero();
        assertThat(usage.get("portfolioEvaluationId")).isNull();
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DELETED);
    }
}
