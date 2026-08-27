CREATE TABLE IF NOT EXISTS request_availability_windows (
  id SERIAL PRIMARY KEY,
  request_id INT NOT NULL REFERENCES scheduling_requests(id) ON DELETE CASCADE,
  window_order INT NOT NULL,
  window_kind VARCHAR(20) NOT NULL,
  expression_type VARCHAR(30) NOT NULL,
  explicit_date DATE NULL,
  day_of_week INT NULL,
  week_offset INT NULL,
  month_offset INT NULL,
  month_of_year INT NULL,
  ordinal_week VARCHAR(20) NULL,
  start_time TIME NULL,
  end_time TIME NULL,
  named_period VARCHAR(20) NULL
);

CREATE INDEX IF NOT EXISTS idx_availability_windows_request ON request_availability_windows (request_id, window_order);

CREATE TABLE IF NOT EXISTS request_availability_intervals (
  id SERIAL PRIMARY KEY,
  request_id INT NOT NULL REFERENCES scheduling_requests(id) ON DELETE CASCADE,
  source_window_id INT NULL REFERENCES request_availability_windows(id) ON DELETE CASCADE,
  window_kind VARCHAR(20) NOT NULL,
  start_instant TIMESTAMPTZ NOT NULL,
  end_instant TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_availability_intervals_request ON request_availability_intervals (request_id, start_instant);
