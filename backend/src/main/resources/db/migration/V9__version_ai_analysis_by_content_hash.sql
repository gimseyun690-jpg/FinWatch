ALTER TABLE news_articles ADD COLUMN final_url VARCHAR(1000);

UPDATE news_articles
SET final_url = canonical_url
WHERE canonical_url IS NOT NULL;

ALTER TABLE ai_analyses ADD COLUMN content_hash VARCHAR(64) NOT NULL DEFAULT 'legacy';

UPDATE ai_analyses
SET content_hash = COALESCE(
    (SELECT news_articles.content_hash FROM news_articles WHERE news_articles.id = ai_analyses.news_id),
    'legacy'
);

ALTER TABLE ai_analyses DROP CONSTRAINT uk_ai_analysis_target_prompt;
ALTER TABLE ai_analyses ADD CONSTRAINT uk_ai_analysis_target_prompt_content
    UNIQUE (news_id, feature_type, prompt_version, content_hash);
