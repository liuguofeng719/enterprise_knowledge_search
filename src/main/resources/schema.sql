CREATE TABLE IF NOT EXISTS kb_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source VARCHAR(32),
  path VARCHAR(512),
  version VARCHAR(64),
  tags VARCHAR(256),
  status VARCHAR(32),
  updated_at TIMESTAMP
);
