ALTER TABLE cloudledger.events
    RENAME COLUMN user_id TO owner_id;

ALTER TABLE cloudledger.events
    ALTER COLUMN owner_id TYPE VARCHAR(255);
