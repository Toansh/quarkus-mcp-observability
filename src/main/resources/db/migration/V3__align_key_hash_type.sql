-- V2 declared key_hash as CHAR(64) (bpchar). The ApiKey entity maps it to a plain
-- String, so Hibernate's post-boot schema validation expects VARCHAR(64) and logs an
-- error on every startup. A SHA-256 hex digest is always exactly 64 chars, so there is
-- no space-padding to lose: this only aligns the declared type. The UNIQUE index on
-- key_hash is rebuilt automatically by the type change.
ALTER TABLE api_keys ALTER COLUMN key_hash TYPE varchar(64);
