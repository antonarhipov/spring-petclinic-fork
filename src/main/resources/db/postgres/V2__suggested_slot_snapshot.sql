ALTER TABLE appointment_request ADD COLUMN suggested_vet_id INT REFERENCES vets (id);
ALTER TABLE appointment_request ADD COLUMN suggested_start_instant TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_appointment_request_suggested_vet_id ON appointment_request (suggested_vet_id);
ALTER TABLE slot_hold ADD COLUMN duration_min INT NOT NULL DEFAULT 30;
