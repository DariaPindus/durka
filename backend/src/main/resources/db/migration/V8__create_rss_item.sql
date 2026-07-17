CREATE TABLE rss_item (
    id           BIGSERIAL PRIMARY KEY,
    feed_url     TEXT NOT NULL,
    feed_title   TEXT,
    external_id  TEXT NOT NULL,
    title        TEXT,
    link         TEXT,
    published_at TIMESTAMPTZ NOT NULL,
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (feed_url, external_id)
);

CREATE INDEX idx_rss_item_feed_published ON rss_item (feed_url, published_at DESC);
