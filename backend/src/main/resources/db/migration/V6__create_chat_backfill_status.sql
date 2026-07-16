CREATE TABLE chat_backfill_status (
    chat_id       BIGINT PRIMARY KEY,
    backfilled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
