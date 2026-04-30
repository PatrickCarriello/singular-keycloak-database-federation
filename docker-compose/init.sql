-- =============================================================================
-- Schema de exemplo para testar o singular-keycloak-database-federation.
-- As queries default do plugin esperam colunas: id, username, email,
-- firstName, lastName, cpf, fullName, hash_pwd.
--
-- Usuários de teste:
--   alice   / secret123  (BCrypt)
--   bob     / secret456  (BCrypt)
--   charlie / secret123  (BCrypt)
-- =============================================================================

CREATE TABLE users (
    "id"        TEXT PRIMARY KEY,
    "username"  TEXT NOT NULL UNIQUE,
    "email"     TEXT,
    "firstName" TEXT,
    "lastName"  TEXT,
    "cpf"       TEXT,
    "fullName"  TEXT,
    hash_pwd    TEXT NOT NULL
);

INSERT INTO users ("id", "username", "email", "firstName", "lastName", "cpf", "fullName", hash_pwd) VALUES
('1', 'alice', 'alice@example.com', 'Alice', 'Silva', '00000000001', 'Alice Silva', '$2b$10$e0OxCM1Gj2w7POQBrZxdMu9n.0..87ycR5J2jfTq4lpSfB065HMHa'),
('2', 'bob', 'bob@example.com', 'Bob', 'Souza', '00000000002', 'Bob Souza', '$2b$10$pzINNRlCfl/yV/23f4H4AOMxPCY6rSRUCtBzCCk216qJkEEqzvHuC'),
('3', 'charlie', 'charlie@example.com', 'Charlie', 'Lima', '00000000003', 'Charlie Lima', '$2b$10$Z1rh84cHjZ7IeP7I4G4JmePnnrEc6kilGo2e4FhZugAqgzYwNUXti');
