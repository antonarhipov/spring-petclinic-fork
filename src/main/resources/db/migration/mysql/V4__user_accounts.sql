CREATE TABLE IF NOT EXISTS user_account (
  id                   INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(100) NOT NULL,
  password             VARCHAR(255) NOT NULL,
  role                 VARCHAR(20) NOT NULL,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  owner_id             INT(4) UNSIGNED,
  INDEX(username),
  INDEX(owner_id),
  UNIQUE (username),
  FOREIGN KEY (owner_id) REFERENCES owners (id)
) engine=InnoDB;
