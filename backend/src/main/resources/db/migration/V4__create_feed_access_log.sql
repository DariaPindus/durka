CREATE TABLE feed_access_log (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address  TEXT NOT NULL,
    user_agent  TEXT,
    path        TEXT NOT NULL,
    token_valid BOOLEAN NOT NULL
);

CREATE INDEX idx_feed_access_log_ip ON feed_access_log (ip_address);
CREATE INDEX idx_feed_access_log_occurred_at ON feed_access_log (occurred_at DESC);
