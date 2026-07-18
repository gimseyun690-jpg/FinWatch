package com.finwatch.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.repository.AiUsageLogRepository;

@Service
public class AiUsageLogWriter {

    private final AiUsageLogRepository repository;

    public AiUsageLogWriter(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(AiUsageLog log) {
        repository.save(log);
    }
}
