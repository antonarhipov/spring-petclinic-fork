CREATE TABLE accounts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  owner_id INT UNSIGNED,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  temporary_credential_expires_at TIMESTAMP(6),
  credential_version BIGINT NOT NULL DEFAULT 0,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_accounts_username UNIQUE (username),
  CONSTRAINT uk_accounts_owner UNIQUE (owner_id),
  CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) engine=InnoDB;

CREATE TABLE SPRING_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME BIGINT NOT NULL,
  LAST_ACCESS_TIME BIGINT NOT NULL,
  MAX_INACTIVE_INTERVAL INT NOT NULL,
  EXPIRY_TIME BIGINT NOT NULL,
  PRINCIPAL_NAME VARCHAR(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) engine=InnoDB;
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES BLOB NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) engine=InnoDB;

CREATE TABLE clinic_scheduling_policies (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  zone_id VARCHAR(64) NOT NULL,
  grid_minutes INTEGER NOT NULL,
  booking_horizon_days INTEGER NOT NULL,
  hold_duration_minutes INTEGER NOT NULL,
  owner_minimum_notice_minutes INTEGER NOT NULL,
  urgent_care_guidance LONGTEXT NOT NULL,
  consent_copy_version VARCHAR(40) NOT NULL,
  configuration_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
) engine=InnoDB;

CREATE TABLE allowed_durations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  duration_minutes INTEGER NOT NULL,
  CONSTRAINT uk_allowed_duration UNIQUE (duration_minutes),
  CONSTRAINT fk_allowed_duration_policy FOREIGN KEY (policy_id) REFERENCES clinic_scheduling_policies (id)
) engine=InnoDB;

CREATE TABLE clinic_hours (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  day_of_week VARCHAR(16) NOT NULL,
  start_local_time TIME NOT NULL,
  end_local_time TIME NOT NULL,
  CONSTRAINT fk_clinic_hours_policy FOREIGN KEY (policy_id) REFERENCES clinic_scheduling_policies (id)
) engine=InnoDB;

CREATE TABLE named_periods (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  code VARCHAR(40) NOT NULL,
  label VARCHAR(80) NOT NULL,
  start_local_time TIME NOT NULL,
  end_local_time TIME NOT NULL,
  CONSTRAINT uk_named_period_code UNIQUE (code),
  CONSTRAINT fk_named_period_policy FOREIGN KEY (policy_id) REFERENCES clinic_scheduling_policies (id)
) engine=InnoDB;

CREATE TABLE emergency_terms (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  term VARCHAR(80) NOT NULL,
  rule_set_version VARCHAR(40) NOT NULL,
  CONSTRAINT uk_emergency_term UNIQUE (term, rule_set_version),
  CONSTRAINT fk_emergency_term_policy FOREIGN KEY (policy_id) REFERENCES clinic_scheduling_policies (id)
) engine=InnoDB;

CREATE TABLE clinic_closures (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  start_local_date DATE NOT NULL,
  end_local_date DATE NOT NULL,
  CONSTRAINT fk_clinic_closure_policy FOREIGN KEY (policy_id) REFERENCES clinic_scheduling_policies (id)
) engine=InnoDB;

CREATE TABLE vet_recurring_shifts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  veterinarian_id INT UNSIGNED NOT NULL,
  day_of_week VARCHAR(16) NOT NULL,
  start_local_time TIME NOT NULL,
  end_local_time TIME NOT NULL,
  CONSTRAINT fk_vet_shift_vet FOREIGN KEY (veterinarian_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE vet_date_exceptions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  veterinarian_id INT UNSIGNED NOT NULL,
  exception_date DATE NOT NULL,
  CONSTRAINT uk_vet_date_exception UNIQUE (veterinarian_id, exception_date),
  CONSTRAINT fk_vet_date_exception_vet FOREIGN KEY (veterinarian_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE vet_date_exception_intervals (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  exception_id BIGINT NOT NULL,
  start_local_time TIME NOT NULL,
  end_local_time TIME NOT NULL,
  CONSTRAINT fk_vet_date_exception_interval FOREIGN KEY (exception_id) REFERENCES vet_date_exceptions (id)
) engine=InnoDB;

CREATE TABLE vet_leave (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  veterinarian_id INT UNSIGNED NOT NULL,
  start_local_date DATE NOT NULL,
  end_local_date DATE NOT NULL,
  CONSTRAINT fk_vet_leave_vet FOREIGN KEY (veterinarian_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE scheduling_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT UNSIGNED NOT NULL,
  pet_id INT UNSIGNED NOT NULL,
  state VARCHAR(40) NOT NULL,
  owner_status_code VARCHAR(80) NOT NULL,
  active_text_revision_id BIGINT,
  active_request_revision_id BIGINT,
  suspected_emergency BOOLEAN NOT NULL DEFAULT FALSE,
  closure_outcome VARCHAR(40),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  closed_at TIMESTAMP(6),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_sched_req_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
  CONSTRAINT fk_sched_req_pet FOREIGN KEY (pet_id) REFERENCES pets (id)
) engine=InnoDB;

CREATE TABLE active_pet_requests (
  pet_id INT UNSIGNED PRIMARY KEY,
  request_id BIGINT NOT NULL,
  CONSTRAINT uk_active_pet_request UNIQUE (request_id),
  CONSTRAINT fk_active_pet_pet FOREIGN KEY (pet_id) REFERENCES pets (id),
  CONSTRAINT fk_active_pet_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;

CREATE TABLE request_text_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  sequence INTEGER NOT NULL,
  source_text VARCHAR(2000) NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  submitted_at TIMESTAMP(6) NOT NULL,
  clinic_zone_id VARCHAR(64) NOT NULL,
  emergency_screen_version VARCHAR(40) NOT NULL,
  emergency_matched_terms_json LONGTEXT,
  CONSTRAINT uk_text_revision_seq UNIQUE (request_id, sequence),
  CONSTRAINT fk_text_revision_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;

CREATE TABLE consent_records (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  text_revision_id BIGINT NOT NULL,
  decision VARCHAR(20) NOT NULL,
  actor_account_id BIGINT NOT NULL,
  data_use_copy_version VARCHAR(40) NOT NULL,
  decided_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_consent_text FOREIGN KEY (text_revision_id) REFERENCES request_text_revisions (id),
  CONSTRAINT fk_consent_account FOREIGN KEY (actor_account_id) REFERENCES accounts (id)
) engine=InnoDB;

CREATE TABLE interpretation_records (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  text_revision_id BIGINT NOT NULL,
  origin VARCHAR(20) NOT NULL,
  schema_version VARCHAR(20),
  recognized_output_json LONGTEXT,
  unknown_fields_json LONGTEXT,
  uncertainties_json LONGTEXT,
  validation_issues_json LONGTEXT,
  outcome VARCHAR(40) NOT NULL,
  created_by_account_id BIGINT,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_interp_text FOREIGN KEY (text_revision_id) REFERENCES request_text_revisions (id),
  CONSTRAINT fk_interp_account FOREIGN KEY (created_by_account_id) REFERENCES accounts (id)
) engine=InnoDB;

CREATE TABLE request_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  sequence INTEGER NOT NULL,
  interpretation_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  visit_reason VARCHAR(500) NOT NULL,
  duration_minutes INTEGER NOT NULL,
  care_type VARCHAR(20) NOT NULL,
  specialty_id INT UNSIGNED,
  urgency VARCHAR(40) NOT NULL,
  preferred_veterinarian_id INT UNSIGNED,
  veterinarian_preference_strength VARCHAR(20) NOT NULL,
  clinic_policy_version BIGINT NOT NULL,
  clinic_zone_id VARCHAR(64) NOT NULL,
  rejection_expiry_count INTEGER NOT NULL DEFAULT 0,
  confirmed_by_account_id BIGINT,
  confirmed_at TIMESTAMP(6),
  created_at TIMESTAMP(6) NOT NULL,
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_request_revision_seq UNIQUE (request_id, sequence),
  CONSTRAINT fk_req_rev_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id),
  CONSTRAINT fk_req_rev_interp FOREIGN KEY (interpretation_id) REFERENCES interpretation_records (id),
  CONSTRAINT fk_req_rev_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id),
  CONSTRAINT fk_req_rev_vet FOREIGN KEY (preferred_veterinarian_id) REFERENCES vets (id),
  CONSTRAINT fk_req_rev_account FOREIGN KEY (confirmed_by_account_id) REFERENCES accounts (id)
) engine=InnoDB;

CREATE TABLE request_windows (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_revision_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  start_at TIMESTAMP(6) NOT NULL,
  end_at TIMESTAMP(6) NOT NULL,
  source_phrase VARCHAR(300),
  named_period_code VARCHAR(40),
  fallback_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_window_revision FOREIGN KEY (request_revision_id) REFERENCES request_revisions (id)
) engine=InnoDB;

CREATE TABLE integration_executions (
  id CHAR(36) PRIMARY KEY,
  kind VARCHAR(40) NOT NULL,
  request_id BIGINT NOT NULL,
  text_revision_id BIGINT,
  request_revision_id BIGINT,
  trigger_key VARCHAR(160) NOT NULL,
  state VARCHAR(40) NOT NULL,
  triggered_at TIMESTAMP(6) NOT NULL,
  deadline_at TIMESTAMP(6) NOT NULL,
  started_at TIMESTAMP(6),
  finished_at TIMESTAMP(6),
  requested_model_id VARCHAR(120),
  resolved_model_id VARCHAR(200),
  solver_configuration_version VARCHAR(120),
  prompt_template_version VARCHAR(40),
  schema_version VARCHAR(40) NOT NULL,
  input_json LONGTEXT,
  normalized_output_json LONGTEXT,
  input_hash VARCHAR(64),
  output_hash VARCHAR(64),
  attempt_count INTEGER NOT NULL DEFAULT 0,
  outcome VARCHAR(80),
  error_classification VARCHAR(80),
  score_json LONGTEXT,
  score_explanation_json LONGTEXT,
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_integration_trigger UNIQUE (trigger_key),
  CONSTRAINT fk_exec_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;

CREATE TABLE integration_attempts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  execution_id CHAR(36) NOT NULL,
  sequence INTEGER NOT NULL,
  started_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6),
  input_json LONGTEXT NOT NULL,
  raw_output LONGTEXT,
  normalized_output_json LONGTEXT,
  unknown_fields_json LONGTEXT,
  score_json LONGTEXT,
  score_explanation_json LONGTEXT,
  outcome VARCHAR(80),
  error_classification VARCHAR(80),
  CONSTRAINT uk_attempt_seq UNIQUE (execution_id, sequence),
  CONSTRAINT fk_attempt_exec FOREIGN KEY (execution_id) REFERENCES integration_executions (id)
) engine=InnoDB;

CREATE TABLE offers (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_revision_id BIGINT NOT NULL,
  execution_id CHAR(36),
  veterinarian_id INT UNSIGNED NOT NULL,
  start_at TIMESTAMP(6) NOT NULL,
  end_at TIMESTAMP(6) NOT NULL,
  duration_minutes INTEGER NOT NULL,
  source VARCHAR(40) NOT NULL,
  classification VARCHAR(20) NOT NULL,
  public_explanation_code VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  owner_rejection_reason VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_offer_revision FOREIGN KEY (request_revision_id) REFERENCES request_revisions (id),
  CONSTRAINT fk_offer_vet FOREIGN KEY (veterinarian_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE appointments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT UNSIGNED NOT NULL,
  veterinarian_id INT UNSIGNED NOT NULL,
  request_id BIGINT,
  offer_id BIGINT,
  start_at TIMESTAMP(6) NOT NULL,
  end_at TIMESTAMP(6) NOT NULL,
  status VARCHAR(20) NOT NULL,
  authorization_basis VARCHAR(40) NOT NULL,
  agreement_recorded_by BIGINT,
  agreement_at TIMESTAMP(6),
  agreement_method VARCHAR(40),
  supporting_visit_id INT UNSIGNED,
  staff_reason_category VARCHAR(80),
  staff_reason_note VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  cancelled_at TIMESTAMP(6),
  completed_at TIMESTAMP(6),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_appt_pet FOREIGN KEY (pet_id) REFERENCES pets (id),
  CONSTRAINT fk_appt_vet FOREIGN KEY (veterinarian_id) REFERENCES vets (id),
  CONSTRAINT fk_appt_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;

CREATE TABLE holds (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  offer_id BIGINT NOT NULL,
  request_id BIGINT NOT NULL,
  state VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  release_reason VARCHAR(40),
  created_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_hold_offer UNIQUE (offer_id),
  CONSTRAINT fk_hold_offer FOREIGN KEY (offer_id) REFERENCES offers (id),
  CONSTRAINT fk_hold_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;

CREATE TABLE reservation_blocks (
  resource_type VARCHAR(20) NOT NULL,
  resource_id INT UNSIGNED NOT NULL,
  block_start TIMESTAMP(6) NOT NULL,
  hold_id BIGINT,
  appointment_id BIGINT,
  CONSTRAINT pk_reservation_block PRIMARY KEY (resource_type, resource_id, block_start),
  CONSTRAINT fk_block_hold FOREIGN KEY (hold_id) REFERENCES holds (id),
  CONSTRAINT fk_block_appt FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) engine=InnoDB;

ALTER TABLE visits ADD COLUMN appointment_id BIGINT;
ALTER TABLE visits ADD COLUMN vet_id INT UNSIGNED;
ALTER TABLE visits ADD CONSTRAINT uk_visits_appointment UNIQUE (appointment_id);
ALTER TABLE visits ADD CONSTRAINT fk_visits_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id);
ALTER TABLE visits ADD CONSTRAINT fk_visits_vet FOREIGN KEY (vet_id) REFERENCES vets (id);

CREATE TABLE staff_queue_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  priority VARCHAR(20) NOT NULL,
  state VARCHAR(40) NOT NULL,
  assignee_account_id BIGINT,
  reason_code VARCHAR(80),
  resolution_code VARCHAR(80),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_queue_request UNIQUE (request_id),
  CONSTRAINT fk_queue_request FOREIGN KEY (request_id) REFERENCES scheduling_requests (id),
  CONSTRAINT fk_queue_assignee FOREIGN KEY (assignee_account_id) REFERENCES accounts (id)
) engine=InnoDB;

CREATE TABLE queue_notes (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  queue_item_id BIGINT NOT NULL,
  author_account_id BIGINT NOT NULL,
  body VARCHAR(2000) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_note_queue FOREIGN KEY (queue_item_id) REFERENCES staff_queue_items (id),
  CONSTRAINT fk_note_author FOREIGN KEY (author_account_id) REFERENCES accounts (id)
) engine=InnoDB;

CREATE TABLE audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_type VARCHAR(20) NOT NULL,
  actor_account_id BIGINT,
  occurred_at TIMESTAMP(6) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id VARCHAR(80) NOT NULL,
  request_id BIGINT,
  before_json LONGTEXT,
  after_json LONGTEXT,
  reason_json LONGTEXT,
  CONSTRAINT fk_audit_account FOREIGN KEY (actor_account_id) REFERENCES accounts (id)
) engine=InnoDB;

INSERT INTO clinic_scheduling_policies (zone_id, grid_minutes, booking_horizon_days, hold_duration_minutes,
  owner_minimum_notice_minutes, urgent_care_guidance, consent_copy_version, configuration_version, created_at, updated_at, version)
VALUES ('America/Chicago', 15, 30, 15, 120, 'If this may be an emergency, contact urgent care immediately.', 'consent-v1', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO allowed_durations (policy_id, duration_minutes) VALUES (1, 15), (1, 30), (1, 45), (1, 60);
INSERT INTO clinic_hours (policy_id, day_of_week, start_local_time, end_local_time) VALUES
  (1, 'MONDAY', '09:00:00', '17:00:00'),
  (1, 'TUESDAY', '09:00:00', '17:00:00'),
  (1, 'WEDNESDAY', '09:00:00', '17:00:00'),
  (1, 'THURSDAY', '09:00:00', '17:00:00'),
  (1, 'FRIDAY', '09:00:00', '17:00:00');
