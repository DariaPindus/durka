CREATE TABLE note (
    id         BIGSERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_note_updated_at ON note (updated_at DESC);

CREATE TABLE task_event (
    id               BIGSERIAL PRIMARY KEY,
    type             TEXT NOT NULL CHECK (type IN ('TASK', 'EVENT')),
    occurs_at        TIMESTAMPTZ NOT NULL,
    description      TEXT NOT NULL,
    duration_minutes INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_event_occurs_at ON task_event (occurs_at);
