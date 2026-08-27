CREATE TABLE IF NOT EXISTS request_availability_windows (
  id INT AUTO_INCREMENT PRIMARY KEY,
  request_id INT UNSIGNED NOT NULL,
  window_order INT NOT NULL,
  window_kind VARCHAR(20) NOT NULL,
  expression_type VARCHAR(30) NOT NULL,
  explicit_date DATE NULL,
  day_of_week INT NULL,
  week_offset INT NULL,
  month_offset INT NULL,
  month_of_year INT NULL,
  ordinal_week VARCHAR(20) NULL,
  start_time TIME(6) NULL,
  end_time TIME(6) NULL,
  named_period VARCHAR(20) NULL,
  CONSTRAINT fk_availability_windows_request FOREIGN KEY (request_id) REFERENCES scheduling_requests(id) ON DELETE CASCADE,
  INDEX idx_availability_windows_request (request_id, window_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS request_availability_intervals (
  id INT AUTO_INCREMENT PRIMARY KEY,
  request_id INT UNSIGNED NOT NULL,
  source_window_id INT NULL,
  window_kind VARCHAR(20) NOT NULL,
  start_instant DATETIME(6) NOT NULL,
  end_instant DATETIME(6) NOT NULL,
  CONSTRAINT fk_availability_intervals_request FOREIGN KEY (request_id) REFERENCES scheduling_requests(id) ON DELETE CASCADE,
  CONSTRAINT fk_availability_intervals_source FOREIGN KEY (source_window_id) REFERENCES request_availability_windows(id) ON DELETE CASCADE,
  INDEX idx_availability_intervals_request (request_id, start_instant)
) ENGINE=InnoDB;
