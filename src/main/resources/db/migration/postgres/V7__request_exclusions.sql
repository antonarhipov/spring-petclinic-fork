CREATE TABLE IF NOT EXISTS request_exclusions (
  id         SERIAL PRIMARY KEY,
  request_id INTEGER NOT NULL,
  vet_id     INTEGER NOT NULL,
  start_time TIMESTAMP NOT NULL,
  created_at TIMESTAMP,
  FOREIGN KEY (request_id) REFERENCES scheduling_requests (id),
  FOREIGN KEY (vet_id) REFERENCES vets (id)
);
CREATE INDEX IF NOT EXISTS idx_request_exclusions_request ON request_exclusions (request_id);
