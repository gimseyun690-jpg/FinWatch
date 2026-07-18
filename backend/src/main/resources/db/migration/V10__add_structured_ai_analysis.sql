ALTER TABLE ai_analyses ADD COLUMN positive_factors TEXT NOT NULL DEFAULT '';
ALTER TABLE ai_analyses ADD COLUMN risk_factors TEXT NOT NULL DEFAULT '';
ALTER TABLE ai_analyses ADD COLUMN mentioned_companies TEXT NOT NULL DEFAULT '';
ALTER TABLE ai_analyses ADD COLUMN evidence_segments TEXT NOT NULL DEFAULT '';
ALTER TABLE ai_analyses ADD COLUMN analysis_scope VARCHAR(30) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE ai_analyses ADD COLUMN original_characters INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_analyses ADD COLUMN processed_characters INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_analyses ADD COLUMN provider_call_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE ai_analyses ADD CONSTRAINT ck_ai_analysis_character_counts
    CHECK (original_characters >= 0 AND processed_characters >= 0);
ALTER TABLE ai_analyses ADD CONSTRAINT ck_ai_analysis_provider_call_count
    CHECK (provider_call_count >= 1);
