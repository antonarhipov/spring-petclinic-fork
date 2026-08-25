CREATE TABLE IF NOT EXISTS slot_occupancy (
  id             INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vet_id         INT(4) UNSIGNED NOT NULL,
  start_time     TIMESTAMP NOT NULL,
  end_time       TIMESTAMP NOT NULL,
  occupancy_type VARCHAR(20) NOT NULL,
  reference_id   INT(4) UNSIGNED,
  created_at     TIMESTAMP NULL,
  INDEX(vet_id, start_time),
  UNIQUE (vet_id, start_time),
  FOREIGN KEY (vet_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS holds (
  id           INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  version      INT NOT NULL DEFAULT 0,
  request_id   INT(4) UNSIGNED NOT NULL,
  vet_id       INT(4) UNSIGNED NOT NULL,
  start_time   TIMESTAMP NOT NULL,
  end_time     TIMESTAMP NOT NULL,
  expires_at   TIMESTAMP NOT NULL,
  status       VARCHAR(20) NOT NULL,
  created_at   TIMESTAMP NULL,
  updated_at   TIMESTAMP NULL,
  INDEX(request_id),
  INDEX(status, expires_at),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests (id),
  FOREIGN KEY (vet_id) REFERENCES vets (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS appointments (
  id           INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  version      INT NOT NULL DEFAULT 0,
  owner_id     INT(4) UNSIGNED NOT NULL,
  pet_id       INT(4) UNSIGNED NOT NULL,
  vet_id       INT(4) UNSIGNED NOT NULL,
  request_id   INT(4) UNSIGNED,
  start_time   TIMESTAMP NOT NULL,
  end_time     TIMESTAMP NOT NULL,
  status       VARCHAR(30) NOT NULL,
  reason       VARCHAR(255),
  created_at   TIMESTAMP NULL,
  updated_at   TIMESTAMP NULL,
  INDEX(owner_id),
  INDEX(pet_id),
  INDEX(vet_id, start_time),
  FOREIGN KEY (owner_id) REFERENCES owners (id),
  FOREIGN KEY (pet_id) REFERENCES pets (id),
  FOREIGN KEY (vet_id) REFERENCES vets (id),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests (id)
) engine=InnoDB;
