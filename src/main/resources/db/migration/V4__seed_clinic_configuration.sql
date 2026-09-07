INSERT INTO clinic_settings (
  booking_horizon_days, minimum_lead_days,
  minimum_duration_minutes, default_duration_minutes, maximum_duration_minutes,
  grid_minutes, morning_start, morning_end, afternoon_start, afternoon_end,
  evening_start, evening_end, time_zone
) VALUES (
  30, 1, 15, 30, 60, 15,
  '09:00:00', '12:00:00', '12:00:00', '17:00:00', '17:00:00', '18:00:00',
  'Europe/Amsterdam'
);

INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'MONDAY', '09:00:00', '17:00:00' FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'TUESDAY', '09:00:00', '17:00:00' FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'WEDNESDAY', '09:00:00', '18:00:00' FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'THURSDAY', '09:00:00', '17:00:00' FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'FRIDAY', '10:00:00', '16:00:00' FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'SATURDAY', NULL, NULL FROM clinic_settings;
INSERT INTO clinic_opening_hours (settings_id, weekday, open_time, close_time)
SELECT id, 'SUNDAY', NULL, NULL FROM clinic_settings;

INSERT INTO vet_working_blocks (vet_id, weekday, start_time, end_time) VALUES
  (1, 'MONDAY', '09:00:00', '17:00:00'),
  (1, 'TUESDAY', '09:00:00', '17:00:00'),
  (1, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (1, 'FRIDAY', '11:00:00', '12:00:00'),
  (2, 'MONDAY', '09:00:00', '17:00:00'),
  (2, 'TUESDAY', '09:00:00', '17:00:00'),
  (2, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (3, 'MONDAY', '09:00:00', '17:00:00'),
  (3, 'TUESDAY', '09:00:00', '17:00:00'),
  (3, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (4, 'THURSDAY', '09:00:00', '17:00:00'),
  (4, 'FRIDAY', '10:00:00', '16:00:00'),
  (5, 'THURSDAY', '09:00:00', '17:00:00'),
  (5, 'FRIDAY', '10:00:00', '16:00:00'),
  (6, 'MONDAY', '13:00:00', '14:00:00'),
  (6, 'THURSDAY', '09:00:00', '17:00:00'),
  (6, 'FRIDAY', '10:00:00', '16:00:00');

INSERT INTO vet_exceptions (vet_id, exception_date) VALUES
  (1, '2026-09-15'),
  (5, '2026-09-15'),
  (5, '2026-09-17'),
  (5, '2026-09-21'),
  (5, '2026-10-22'),
  (6, '2026-10-22');
