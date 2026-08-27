ALTER TABLE vets ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE specialties ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;

CREATE TABLE IF NOT EXISTS account_guard (
  id INT(4) UNSIGNED NOT NULL PRIMARY KEY
) engine=InnoDB;
INSERT IGNORE INTO account_guard (id) VALUES (1);

CREATE TABLE IF NOT EXISTS accounts (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  active BOOLEAN DEFAULT TRUE NOT NULL,
  password_change_required BOOLEAN DEFAULT FALSE NOT NULL,
  owner_id INT(4) UNSIGNED NULL,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE (username),
  UNIQUE (owner_id),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  INDEX (username)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS clinic_settings (
  id INT(4) UNSIGNED NOT NULL PRIMARY KEY,
  min_duration_minutes INT NOT NULL DEFAULT 15,
  max_duration_minutes INT NOT NULL DEFAULT 120,
  default_duration_minutes INT NOT NULL DEFAULT 30,
  owner_booking_horizon_days INT NOT NULL DEFAULT 56,
  staff_booking_horizon_days INT NOT NULL DEFAULT 365,
  guided_hold_duration_minutes INT NOT NULL DEFAULT 5,
  staff_offer_hold_duration_hours INT NOT NULL DEFAULT 24,
  staff_claim_inactivity_minutes INT NOT NULL DEFAULT 30,
  fallback_deadline_days INT NOT NULL DEFAULT 7,
  sensitive_data_retention_days INT NOT NULL DEFAULT 30,
  lockout_duration_minutes INT NOT NULL DEFAULT 15,
  lockout_threshold INT NOT NULL DEFAULT 5,
  zone_id VARCHAR(50) NOT NULL DEFAULT 'UTC',
  morning_start_time TIME NOT NULL DEFAULT '08:00:00',
  morning_end_time TIME NOT NULL DEFAULT '12:00:00',
  afternoon_start_time TIME NOT NULL DEFAULT '12:00:00',
  afternoon_end_time TIME NOT NULL DEFAULT '17:00:00',
  evening_start_time TIME NOT NULL DEFAULT '17:00:00',
  evening_end_time TIME NOT NULL DEFAULT '20:00:00',
  urgent_care_guidance VARCHAR(1000) NOT NULL,
  emergency_phone VARCHAR(50) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
) engine=InnoDB;

INSERT IGNORE INTO clinic_settings (
  id, min_duration_minutes, max_duration_minutes, default_duration_minutes,
  owner_booking_horizon_days, staff_booking_horizon_days,
  guided_hold_duration_minutes, staff_offer_hold_duration_hours, staff_claim_inactivity_minutes,
  fallback_deadline_days, sensitive_data_retention_days,
  lockout_duration_minutes, lockout_threshold,
  zone_id, morning_start_time, morning_end_time,
  afternoon_start_time, afternoon_end_time,
  evening_start_time, evening_end_time,
  urgent_care_guidance, emergency_phone, version
) VALUES (
  1, 15, 120, 30,
  56, 365,
  5, 24, 30,
  7, 30,
  15, 5,
  'UTC', '08:00:00', '12:00:00',
  '12:00:00', '17:00:00',
  '17:00:00', '20:00:00',
  'If your pet requires immediate medical attention, please visit the emergency clinic or call our urgent care line.',
  '608-555-0199', 0
);

CREATE TABLE IF NOT EXISTS vet_schedules (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  day_of_week INT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  shift_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  INDEX (vet_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_schedule_exceptions (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  start_time TIME NULL,
  end_time TIME NULL,
  exception_type VARCHAR(30) NOT NULL,
  reason VARCHAR(255) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  INDEX (vet_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS clinic_closures (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(255) NULL,
  version BIGINT NOT NULL DEFAULT 0
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS scheduling_requests (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT(4) UNSIGNED NOT NULL,
  pet_id INT(4) UNSIGNED NOT NULL,
  status VARCHAR(40) NOT NULL,
  original_text VARCHAR(2000) NULL,
  language VARCHAR(10) NOT NULL DEFAULT 'en',
  urgent BOOLEAN NOT NULL DEFAULT FALSE,
  care_type VARCHAR(30) NULL,
  required_specialty_id INT(4) UNSIGNED NULL,
  preferred_vet_id INT(4) UNSIGNED NULL,
  duration_minutes INT NULL,
  preferred_start_window DATETIME(6) NULL,
  preferred_end_window DATETIME(6) NULL,
  owner_horizon_end DATETIME(6) NULL,
  first_queued_at DATETIME(6) NULL,
  fallback_deadline DATETIME(6) NULL,
  last_clarification_reason VARCHAR(255) NULL,
  clarification_count INT NOT NULL DEFAULT 0,
  ai_summary VARCHAR(1000) NULL,
  full_ai_response TEXT NULL,
  retention_deadline DATETIME(6) NULL,
  purged_at DATETIME(6) NULL,
  consent_given_at DATETIME(6) NULL,
  recovery_required BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (pet_id) REFERENCES pets(id),
  FOREIGN KEY (required_specialty_id) REFERENCES specialties(id),
  FOREIGN KEY (preferred_vet_id) REFERENCES vets(id),
  INDEX (owner_id),
  INDEX (status)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS operations (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(100) NOT NULL,
  request_id INT(4) UNSIGNED NOT NULL,
  operation_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  dispatched_at DATETIME(6) NOT NULL,
  deadline DATETIME(6) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (token),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id),
  INDEX (request_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS reservations (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reservation_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  request_id INT(4) UNSIGNED NOT NULL,
  owner_id INT(4) UNSIGNED NOT NULL,
  pet_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (pet_id) REFERENCES pets(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  INDEX (request_id),
  INDEX (vet_id, start_time, end_time)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS rejections (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  reason VARCHAR(255) NULL,
  rejected_at DATETIME(6) NOT NULL,
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  INDEX (request_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS staff_claims (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id INT(4) UNSIGNED NOT NULL,
  staff_username VARCHAR(100) NOT NULL,
  claimed_at DATETIME(6) NOT NULL,
  last_activity_at DATETIME(6) NOT NULL,
  reclaimable_after DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (request_id),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id),
  INDEX (staff_username)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS appointments (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  status VARCHAR(30) NOT NULL,
  request_id INT(4) UNSIGNED NULL,
  owner_id INT(4) UNSIGNED NOT NULL,
  pet_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  care_type VARCHAR(30) NOT NULL,
  required_specialty_id INT(4) UNSIGNED NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  booking_source VARCHAR(30) NOT NULL,
  offline_reason VARCHAR(255) NULL,
  cancellation_reason VARCHAR(255) NULL,
  completed_at DATETIME(6) NULL,
  completion_notes VARCHAR(1000) NULL,
  visit_id INT(4) UNSIGNED NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  FOREIGN KEY (request_id) REFERENCES scheduling_requests(id),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (pet_id) REFERENCES pets(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (required_specialty_id) REFERENCES specialties(id),
  FOREIGN KEY (visit_id) REFERENCES visits(id),
  INDEX (owner_id),
  INDEX (vet_id, start_time, end_time)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS calendar_conflicts (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  appointment_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  conflict_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  detected_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  INDEX (appointment_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT(4) UNSIGNED NOT NULL,
  title_key VARCHAR(100) NOT NULL,
  message_key VARCHAR(100) NOT NULL,
  message_params VARCHAR(500) NULL,
  target_url VARCHAR(255) NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  INDEX (owner_id, is_read)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS audit_records (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(50) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  actor_role VARCHAR(20) NOT NULL,
  target_entity_type VARCHAR(50) NOT NULL,
  target_entity_id VARCHAR(50) NOT NULL,
  reason_code VARCHAR(50) NULL,
  metadata_json TEXT NULL,
  occurred_at DATETIME(6) NOT NULL,
  INDEX (occurred_at),
  INDEX (target_entity_type, target_entity_id)
) engine=InnoDB;
