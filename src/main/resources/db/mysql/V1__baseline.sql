CREATE TABLE IF NOT EXISTS vets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS specialties (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INT(4) UNSIGNED NOT NULL,
  specialty_id INT(4) UNSIGNED NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  UNIQUE (vet_id,specialty_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS types (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS owners (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS pets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(30),
  birth_date DATE,
  type_id INT(4) UNSIGNED NOT NULL,
  owner_id INT(4) UNSIGNED,
  INDEX(name),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (type_id) REFERENCES types(id),
  UNIQUE (owner_id, name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS visits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED,
  visit_date DATE,
  description VARCHAR(255),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS app_user (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  owner_id INT(4) UNSIGNED,
  UNIQUE (username),
  FOREIGN KEY (owner_id) REFERENCES owners(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS clinic_settings (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  grid_granularity_min INT NOT NULL DEFAULT 15,
  hold_duration_min INT NOT NULL DEFAULT 10,
  booking_horizon_days INT NOT NULL DEFAULT 60,
  min_visit_min INT NOT NULL DEFAULT 15,
  max_visit_min INT NOT NULL DEFAULT 120,
  default_visit_min INT NOT NULL DEFAULT 30,
  zone_id VARCHAR(50) NOT NULL DEFAULT 'Europe/Amsterdam',
  morning_start TIME NOT NULL DEFAULT '08:00:00',
  morning_end TIME NOT NULL DEFAULT '12:00:00',
  afternoon_start TIME NOT NULL DEFAULT '12:00:00',
  afternoon_end TIME NOT NULL DEFAULT '17:00:00',
  evening_start TIME NOT NULL DEFAULT '17:00:00',
  evening_end TIME NOT NULL DEFAULT '20:00:00'
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_weekly_shift (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  day_of_week INT NOT NULL,
  start_local TIME NOT NULL,
  end_local TIME NOT NULL,
  INDEX(vet_id),
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_availability_exception (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  date DATE NOT NULL,
  type VARCHAR(20) NOT NULL,
  start_local TIME,
  end_local TIME,
  INDEX(vet_id, date),
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_leave (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  INDEX(vet_id),
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS clinic_closure (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  INDEX(from_date, to_date)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS appointment_request (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT(4) UNSIGNED NOT NULL,
  pet_id INT(4) UNSIGNED NOT NULL,
  free_text TEXT,
  status VARCHAR(30) NOT NULL,
  consent_flag BOOLEAN NOT NULL DEFAULT FALSE,
  consent_at DATETIME,
  consent_text_snapshot TEXT,
  interpretation_json TEXT,
  resulting_appointment_id INT(4) UNSIGNED,
  active_hold_id INT(4) UNSIGNED,
  INDEX(owner_id),
  INDEX(pet_id),
  INDEX(status),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS appointment (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id INT(4) UNSIGNED,
  pet_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_instant DATETIME NOT NULL,
  duration_min INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  reason VARCHAR(500),
  INDEX(pet_id),
  INDEX(vet_id),
  INDEX(start_instant),
  FOREIGN KEY (request_id) REFERENCES appointment_request(id),
  FOREIGN KEY (pet_id) REFERENCES pets(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS slot_hold (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_instant DATETIME NOT NULL,
  request_id INT(4) UNSIGNED NOT NULL,
  expires_at DATETIME NOT NULL,
  UNIQUE (vet_id, start_instant),
  INDEX(expires_at),
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (request_id) REFERENCES appointment_request(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS rejected_suggestion (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id INT(4) UNSIGNED NOT NULL,
  vet_id INT(4) UNSIGNED NOT NULL,
  start_instant DATETIME NOT NULL,
  INDEX(request_id),
  FOREIGN KEY (request_id) REFERENCES appointment_request(id),
  FOREIGN KEY (vet_id) REFERENCES vets(id)
) engine=InnoDB;

ALTER TABLE appointment_request ADD CONSTRAINT fk_app_req_resulting_app FOREIGN KEY (resulting_appointment_id) REFERENCES appointment(id);
ALTER TABLE appointment_request ADD CONSTRAINT fk_app_req_active_hold FOREIGN KEY (active_hold_id) REFERENCES slot_hold(id);

-- Seed Data
INSERT IGNORE INTO vets VALUES (1, 'James', 'Carter');
INSERT IGNORE INTO vets VALUES (2, 'Helen', 'Leary');
INSERT IGNORE INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT IGNORE INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT IGNORE INTO vets VALUES (5, 'Henry', 'Stevens');
INSERT IGNORE INTO vets VALUES (6, 'Sharon', 'Jenkins');

INSERT IGNORE INTO specialties VALUES (1, 'radiology');
INSERT IGNORE INTO specialties VALUES (2, 'surgery');
INSERT IGNORE INTO specialties VALUES (3, 'dentistry');

INSERT IGNORE INTO vet_specialties VALUES (2, 1);
INSERT IGNORE INTO vet_specialties VALUES (3, 2);
INSERT IGNORE INTO vet_specialties VALUES (3, 3);
INSERT IGNORE INTO vet_specialties VALUES (4, 2);
INSERT IGNORE INTO vet_specialties VALUES (5, 1);

INSERT IGNORE INTO types VALUES (1, 'cat');
INSERT IGNORE INTO types VALUES (2, 'dog');
INSERT IGNORE INTO types VALUES (3, 'lizard');
INSERT IGNORE INTO types VALUES (4, 'snake');
INSERT IGNORE INTO types VALUES (5, 'bird');
INSERT IGNORE INTO types VALUES (6, 'hamster');

INSERT IGNORE INTO owners VALUES (1, 'George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT IGNORE INTO owners VALUES (2, 'Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT IGNORE INTO owners VALUES (3, 'Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT IGNORE INTO owners VALUES (4, 'Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT IGNORE INTO owners VALUES (5, 'Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT IGNORE INTO owners VALUES (6, 'Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT IGNORE INTO owners VALUES (7, 'Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT IGNORE INTO owners VALUES (8, 'Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT IGNORE INTO owners VALUES (9, 'David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT IGNORE INTO owners VALUES (10, 'Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');

INSERT IGNORE INTO pets VALUES (1, 'Leo', '2000-09-07', 1, 1);
INSERT IGNORE INTO pets VALUES (2, 'Basil', '2002-08-06', 6, 2);
INSERT IGNORE INTO pets VALUES (3, 'Rosy', '2001-04-17', 2, 3);
INSERT IGNORE INTO pets VALUES (4, 'Jewel', '2000-03-07', 2, 3);
INSERT IGNORE INTO pets VALUES (5, 'Iggy', '2000-11-30', 3, 4);
INSERT IGNORE INTO pets VALUES (6, 'George', '2000-01-20', 4, 5);
INSERT IGNORE INTO pets VALUES (7, 'Samantha', '1995-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (8, 'Max', '1995-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (9, 'Lucky', '1999-08-06', 5, 7);
INSERT IGNORE INTO pets VALUES (10, 'Mulligan', '1997-02-24', 2, 8);
INSERT IGNORE INTO pets VALUES (11, 'Freddy', '2000-03-09', 5, 9);
INSERT IGNORE INTO pets VALUES (12, 'Lucky', '2000-06-24', 2, 10);
INSERT IGNORE INTO pets VALUES (13, 'Sly', '2002-06-08', 1, 10);

INSERT IGNORE INTO visits VALUES (1, 7, '2010-03-04', 'rabies shot');
INSERT IGNORE INTO visits VALUES (2, 8, '2011-03-04', 'rabies shot');
INSERT IGNORE INTO visits VALUES (3, 8, '2009-06-04', 'neutered');
INSERT IGNORE INTO visits VALUES (4, 7, '2008-09-04', 'spayed');

INSERT IGNORE INTO clinic_settings VALUES (1, 15, 10, 60, 15, 120, 30, 'Europe/Amsterdam', '08:00:00', '12:00:00', '12:00:00', '17:00:00', '17:00:00', '20:00:00');
