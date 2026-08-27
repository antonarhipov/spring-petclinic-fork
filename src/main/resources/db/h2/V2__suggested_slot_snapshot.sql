ALTER TABLE appointment_request ADD suggested_vet_id INTEGER;
ALTER TABLE appointment_request ADD suggested_start_instant TIMESTAMP;
ALTER TABLE appointment_request ADD CONSTRAINT fk_app_req_suggested_vet
  FOREIGN KEY (suggested_vet_id) REFERENCES vets (id);
CREATE INDEX app_req_suggested_vet_id ON appointment_request (suggested_vet_id);
ALTER TABLE slot_hold ADD duration_min INTEGER NOT NULL DEFAULT 30;
