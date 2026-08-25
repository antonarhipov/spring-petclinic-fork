CREATE TABLE IF NOT EXISTS request_exclusions (
  id         INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id INT(4) UNSIGNED NOT NULL,
  vet_id     INT(4) UNSIGNED NOT NULL,
  start_time TIMESTAMP NOT NULL,
  created_at TIMESTAMP NULL,
  INDEX(request_id),
  FOREIGN KEY (request_id) REFERENCES scheduling_requests (id),
  FOREIGN KEY (vet_id) REFERENCES vets (id)
) engine=InnoDB;
