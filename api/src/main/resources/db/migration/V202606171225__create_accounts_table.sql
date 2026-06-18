CREATE TABLE accounts (
    id   UUID         NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);
