-- User.internalAuthId (@Column(name = "internal_auth_id")) has no
-- corresponding column, so Hibernate's schema validation fails on startup
-- with "missing column [internal_auth_id] in table [users]".
-- It's looked up via UserRepository.findByInternalAuthId() to resolve the
-- current user from the internal-auth JWT subject, so it's indexed and
-- unique (nullable, since existing rows won't have a value backfilled yet).
ALTER TABLE users
    ADD COLUMN internal_auth_id VARCHAR(255);

CREATE UNIQUE INDEX idx_users_internal_auth_id
    ON users (internal_auth_id);
