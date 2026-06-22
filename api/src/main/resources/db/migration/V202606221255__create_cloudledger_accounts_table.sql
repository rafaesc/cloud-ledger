DROP TABLE accounts;

CREATE TABLE accounts (
    id       TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    currency TEXT NOT NULL
);
