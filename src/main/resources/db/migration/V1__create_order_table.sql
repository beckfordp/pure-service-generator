-- "order" is quoted throughout: it's a reserved keyword in PostgreSQL and can't be
-- used as an unquoted identifier.
CREATE TABLE "order" (
    id UUID PRIMARY KEY,
    item TEXT NOT NULL,
    quantity INT NOT NULL,
    status TEXT NOT NULL DEFAULT 'created',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
