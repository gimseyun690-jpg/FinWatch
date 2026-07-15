ALTER TABLE news_articles ADD COLUMN content_kind VARCHAR(30) NOT NULL DEFAULT 'NEWS';
ALTER TABLE news_articles ADD COLUMN disclosure_type VARCHAR(80);

UPDATE news_articles
SET content_kind = 'DISCLOSURE'
WHERE source IN ('OPENDART', 'SEC_EDGAR');

CREATE INDEX idx_news_stock_kind_published
    ON news_articles (stock_id, content_kind, published_at DESC);
