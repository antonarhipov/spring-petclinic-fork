CREATE TABLE IF NOT EXISTS clinic_settings (
  id                    INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  time_zone             VARCHAR(50) NOT NULL,
  min_visit_minutes     INT NOT NULL,
  max_visit_minutes     INT NOT NULL,
  default_visit_minutes INT NOT NULL,
  booking_horizon_days  INT NOT NULL,
  hold_duration_minutes INT NOT NULL,
  grid_minutes          INT NOT NULL DEFAULT 15
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS part_of_day (
  id         INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(50) NOT NULL,
  start_time TIME NOT NULL,
  end_time   TIME NOT NULL,
  UNIQUE (name)
) engine=InnoDB;

INSERT INTO clinic_settings (time_zone, min_visit_minutes, max_visit_minutes, default_visit_minutes, booking_horizon_days, hold_duration_minutes, grid_minutes)
VALUES ('America/New_York', 15, 120, 30, 14, 5, 15);

INSERT INTO part_of_day (name, start_time, end_time) VALUES ('MORNING', '08:00:00', '12:00:00');
INSERT INTO part_of_day (name, start_time, end_time) VALUES ('AFTERNOON', '12:00:00', '17:00:00');
INSERT INTO part_of_day (name, start_time, end_time) VALUES ('EVENING', '17:00:00', '20:00:00');
