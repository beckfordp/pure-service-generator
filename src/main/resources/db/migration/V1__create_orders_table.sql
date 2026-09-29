CREATE TABLE orders (
    id UUID PRIMARY KEY,
    item TEXT NOT NULL,
    quantity INT NOT NULL,
    status TEXT NOT NULL DEFAULT 'created',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
