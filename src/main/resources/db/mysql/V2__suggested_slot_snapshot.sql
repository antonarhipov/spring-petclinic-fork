ALTER TABLE appointment_request
  ADD suggested_vet_id INT(4) UNSIGNED,
  ADD suggested_start_instant DATETIME,
  ADD INDEX (suggested_vet_id),
  ADD CONSTRAINT fk_app_req_suggested_vet FOREIGN KEY (suggested_vet_id) REFERENCES vets(id);

ALTER TABLE slot_hold ADD duration_min INT NOT NULL DEFAULT 30;
