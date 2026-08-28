CREATE TABLE accounts (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL, role VARCHAR(16) NOT NULL, owner_id INT UNSIGNED UNIQUE,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE, temporary_password_expires_at TIMESTAMP NULL,
  failed_sign_in_count INT UNSIGNED NOT NULL DEFAULT 0, locked_until TIMESTAMP NULL, version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (owner_id) REFERENCES owners(id)
) ENGINE=InnoDB;
CREATE TABLE clinic_scheduling_settings (
  id INT UNSIGNED PRIMARY KEY, clinic_zone VARCHAR(64) NOT NULL, booking_horizon_days INT UNSIGNED NOT NULL,
  owner_minimum_notice_minutes INT UNSIGNED NOT NULL, offer_hold_minutes INT UNSIGNED NOT NULL,
  urgent_care_guidance VARCHAR(2000) NOT NULL, version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO clinic_scheduling_settings VALUES (1, 'Europe/Amsterdam', 90, 120, 10, 'If your pet needs urgent care, contact the clinic or an emergency veterinarian immediately.', 0);
CREATE TABLE named_day_periods (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, start_time TIME NOT NULL,
  end_time TIME NOT NULL, version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE recurring_vet_shifts (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, vet_id INT UNSIGNED NOT NULL, day_of_week INT UNSIGNED NOT NULL,
  start_time TIME NOT NULL, end_time TIME NOT NULL, version BIGINT NOT NULL DEFAULT 0, FOREIGN KEY (vet_id) REFERENCES vets(id)
) ENGINE=InnoDB;
CREATE TABLE vet_availability_exceptions (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, vet_id INT UNSIGNED NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
  available BOOLEAN NOT NULL, start_time TIME NULL, end_time TIME NULL, reason VARCHAR(255), version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) ENGINE=InnoDB;
CREATE TABLE vet_leaves (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, vet_id INT UNSIGNED NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
  reason VARCHAR(255), version BIGINT NOT NULL DEFAULT 0, FOREIGN KEY (vet_id) REFERENCES vets(id)
) ENGINE=InnoDB;
CREATE TABLE clinic_closures (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, start_date DATE NOT NULL, end_date DATE NOT NULL, reason VARCHAR(255),
  version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE scheduling_requests (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, pet_id INT UNSIGNED NOT NULL, state VARCHAR(32) NOT NULL,
  current_revision_id INT UNSIGNED NULL, emergency_priority BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) ENGINE=InnoDB;
CREATE TABLE request_revisions (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, request_id INT UNSIGNED NOT NULL, revision_number INT UNSIGNED NOT NULL,
  source_text VARCHAR(2000) NOT NULL, consent_given BOOLEAN NOT NULL, consent_at TIMESTAMP NULL, raw_interpretation TEXT,
  model_identifier VARCHAR(255), correlation_id VARCHAR(80) NOT NULL, visit_reason VARCHAR(1000), duration_minutes INT UNSIGNED,
  care_type VARCHAR(80), required_specialty VARCHAR(80), preferred_vet_id INT UNSIGNED NULL, urgency VARCHAR(32), confirmed_at TIMESTAMP NULL,
  status VARCHAR(32) NOT NULL, version BIGINT NOT NULL DEFAULT 0, UNIQUE (request_id, revision_number),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id), FOREIGN KEY (preferred_vet_id) REFERENCES vets(id)
) ENGINE=InnoDB;
ALTER TABLE scheduling_requests ADD FOREIGN KEY (current_revision_id) REFERENCES request_revisions(id);
CREATE TABLE request_availability_windows (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, revision_id INT UNSIGNED NOT NULL, kind VARCHAR(16) NOT NULL, applicable_date DATE NULL,
  day_of_week INT UNSIGNED NULL, start_time TIME NOT NULL, end_time TIME NOT NULL, source VARCHAR(32) NOT NULL,
  FOREIGN KEY (revision_id) REFERENCES request_revisions(id)
) ENGINE=InnoDB;
CREATE TABLE appointment_offers (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, revision_id INT UNSIGNED NOT NULL, vet_id INT UNSIGNED NOT NULL, start_at TIMESTAMP NOT NULL,
  duration_minutes INT UNSIGNED NOT NULL, offered_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, state VARCHAR(16) NOT NULL,
  rationale VARCHAR(1000) NOT NULL, rejection_reason VARCHAR(255), version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (revision_id, vet_id, start_at), FOREIGN KEY (revision_id) REFERENCES request_revisions(id), FOREIGN KEY (vet_id) REFERENCES vets(id)
) ENGINE=InnoDB;
CREATE TABLE appointments (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, pet_id INT UNSIGNED NOT NULL, vet_id INT UNSIGNED NOT NULL, start_at TIMESTAMP NOT NULL,
  duration_minutes INT UNSIGNED NOT NULL, source VARCHAR(32) NOT NULL, request_id INT UNSIGNED NULL, revision_id INT UNSIGNED NULL, offer_id INT UNSIGNED UNIQUE NULL,
  owner_agreement VARCHAR(1000), status VARCHAR(16) NOT NULL, change_reason VARCHAR(64), change_note VARCHAR(1000),
  version BIGINT NOT NULL DEFAULT 0, FOREIGN KEY (pet_id) REFERENCES pets(id), FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id), FOREIGN KEY (revision_id) REFERENCES request_revisions(id),
  FOREIGN KEY (offer_id) REFERENCES appointment_offers(id)
) ENGINE=InnoDB;
CREATE TABLE reservation_blocks (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, resource_type VARCHAR(16) NOT NULL, resource_id INT UNSIGNED NOT NULL,
  slot_start TIMESTAMP NOT NULL, owner_type VARCHAR(16) NOT NULL, owner_id INT UNSIGNED NOT NULL, state VARCHAR(16) NOT NULL,
  expires_at TIMESTAMP NULL, UNIQUE (resource_type, resource_id, slot_start)
) ENGINE=InnoDB;
CREATE TABLE staff_queue_items (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, request_id INT UNSIGNED NOT NULL UNIQUE, state VARCHAR(32) NOT NULL, priority VARCHAR(16) NOT NULL,
  claimed_by_account_id INT UNSIGNED NULL, resolution_details VARCHAR(1000), version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id), FOREIGN KEY (claimed_by_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB;
CREATE TABLE scheduling_audit_records (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, occurred_at TIMESTAMP NOT NULL, correlation_id VARCHAR(80), actor_account_id INT UNSIGNED NULL,
  actor VARCHAR(80) NOT NULL, action VARCHAR(64) NOT NULL, target_type VARCHAR(64) NOT NULL, target_id VARCHAR(80) NOT NULL,
  prior_value TEXT, resulting_value TEXT, reason VARCHAR(1000)
) ENGINE=InnoDB;
ALTER TABLE visits ADD appointment_id INT UNSIGNED UNIQUE NULL, ADD vet_id INT UNSIGNED NULL, ADD completed_at TIMESTAMP NULL, ADD clinical_notes VARCHAR(2000) NULL;
ALTER TABLE visits ADD FOREIGN KEY (appointment_id) REFERENCES appointments(id), ADD FOREIGN KEY (vet_id) REFERENCES vets(id);
