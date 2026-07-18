CREATE INDEX idx_news_kind_published_id
    ON news_articles (content_kind, published_at DESC, id DESC);

CREATE INDEX idx_news_source_published_id
    ON news_articles (source, published_at DESC, id DESC);

CREATE INDEX idx_news_published_id
    ON news_articles (published_at DESC, id DESC);

CREATE INDEX idx_ai_analyses_news_feature_generated
    ON ai_analyses (news_id, feature_type, generated_at DESC, id DESC);
