CREATE TABLE feed_item (
    id                BIGSERIAL PRIMARY KEY,
    external_id       TEXT NOT NULL UNIQUE,
    chat_id           BIGINT NOT NULL,
    message_id        BIGINT NOT NULL,
    from_display_name TEXT,
    from_username     TEXT,
    occurred_at       TIMESTAMPTZ NOT NULL,
    text              TEXT NOT NULL DEFAULT '',
    is_seen           BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (chat_id, message_id)
);

CREATE INDEX idx_feed_item_unseen ON feed_item (is_seen, occurred_at DESC);
