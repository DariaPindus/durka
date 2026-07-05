CREATE TABLE telegram_session_status (
    phone_number        TEXT PRIMARY KEY,
    telegram_user_id    BIGINT,
    authorization_state TEXT NOT NULL,
    last_verified_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
