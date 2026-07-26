-- Adds the wallet's offline-transfer signing public key.
--
-- Part of the signed-offline-transfer feature: the server verifies each P2P
-- transfer against the sender wallet's registered ECDSA P-256 public key
-- (see TransferSignatureVerifier / WalletSigningKeyService). Stored as
-- SPKI/X.509 base64 (~124 chars for P-256); VARCHAR(512) gives headroom and
-- matches the entity's @Column(name = "signing_public_key", length = 512).
--
-- Nullable on purpose: existing wallets have no key yet and enroll one on
-- their next login, so the column must accept NULL for them to migrate
-- cleanly. Required for prod, which runs spring.jpa.hibernate.ddl-auto=validate
-- against the entity — without this column, validation fails at startup.
--
-- Flyway note: V1 is already baselined in production and must not be edited,
-- so this change ships as a new versioned migration. New environments run V1
-- (full baseline) then this script; existing prod baselines V1 (not executed)
-- then runs this.

ALTER TABLE wallets
    ADD COLUMN signing_public_key VARCHAR(512);