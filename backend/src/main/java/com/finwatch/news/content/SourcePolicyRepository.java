package com.finwatch.news.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourcePolicyRepository extends JpaRepository<SourcePolicy, Long> {

    List<SourcePolicy> findAllByEnabledTrue();
}
