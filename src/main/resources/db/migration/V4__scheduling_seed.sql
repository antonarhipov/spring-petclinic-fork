INSERT INTO users (id, username, password, role, owner_id) VALUES
  (default, 'george', '$2a$10$XzGzqHVPFRz3x1vlck5zeOOzz9pMsD2DpgXDuhcOZU5jX3Xgo1K3G', 'owner', 1),
  (default, 'betty', '$2a$10$mTIy60d8CDXQPvzs9Q.Jy.jDp2n6vBILU8BsQXh.FthuoIdRuGd1e', 'owner', 2),
  (default, 'eduardo', '$2a$10$5tJgv4UYDV.iNqdbedpSmehJqjm.TJ/ZQl3gYnw/6ifzTak1NmPSW', 'owner', 3),
  (default, 'harold', '$2a$10$pB.tGwfaI47M1jRuiQ5VBeRq14K40jYGf4QEVI.ei6uTm78FvlAl.', 'owner', 4),
  (default, 'peter', '$2a$10$GCd7RrXcYyNLHEktns1Oq.mu5fscSmzpwFXeXMYxuW/WIs/y613W2', 'owner', 5),
  (default, 'jean', '$2a$10$K0EHGWLMCQi7QYT3zmlmaed74xBZpPPzK8tiSm3/sLQjOGukxYvgu', 'owner', 6),
  (default, 'jeff', '$2a$10$dSaIxM.Pd8OmAYSmKVHqguecdxuStXskH8TutDWYz9JnRlwdByswW', 'owner', 7),
  (default, 'maria', '$2a$10$2O6sA6nzKr220XKIJWs13eASit8JizGxmrSID/O//nyLviDI9fTGy', 'owner', 8),
  (default, 'david', '$2a$10$UAM5r1n9ZG40gLwNUp2NtuGGNIxMRXEdqDW7gNJUlkWg.7KR3m3M.', 'owner', 9),
  (default, 'carlos', '$2a$10$wFF1McJHbz.mx4lEP15UvOpJx/Hb8zjT1NGz3a0m/PKZCQgVbKl7y', 'owner', 10),
  (default, 'admin', '$2a$10$kxZ0pEobRj416GW.2fQj7e8SG60.OYzw8eFYOmBn5Dx1/RVOXjdKu', 'staff', NULL),
  (default, 'staff', '$2a$10$fxTyc.X9g.XfnngoI1f4SuZsUmfIgr4E83K72JvJp48S2.6iAzUgy', 'staff', NULL);

INSERT INTO clinic_opening_hour (id, clinic_name, day_of_week, open_time, close_time, closed) VALUES
  (default, 'Clinic A', 'MONDAY', '09:00:00', '17:00:00', FALSE),
  (default, 'Clinic A', 'TUESDAY', '09:00:00', '17:00:00', FALSE),
  (default, 'Clinic A', 'WEDNESDAY', '09:00:00', '18:00:00', FALSE),
  (default, 'Clinic A', 'THURSDAY', '09:00:00', '17:00:00', FALSE),
  (default, 'Clinic A', 'FRIDAY', '10:00:00', '16:00:00', FALSE),
  (default, 'Clinic A', 'SATURDAY', NULL, NULL, TRUE),
  (default, 'Clinic A', 'SUNDAY', NULL, NULL, TRUE);

INSERT INTO clinic_part_of_day (id, name, start_time, end_time) VALUES
  (default, 'morning', '09:00:00', '12:00:00'),
  (default, 'afternoon', '12:00:00', '17:00:00'),
  (default, 'evening', '17:00:00', '18:00:00');

INSERT INTO clinic_config (id, booking_horizon_days, min_duration_minutes, max_duration_minutes, default_duration_minutes, grid_interval_minutes, time_zone, emergency_phone) VALUES
  (default, 30, 15, 60, 30, 15, 'Europe/Amsterdam', '555-0199');

INSERT INTO vet_weekly_block (id, vet_id, day_of_week, start_time, end_time) VALUES
  (default, 1, 'MONDAY', '09:00:00', '17:00:00'),
  (default, 1, 'TUESDAY', '09:00:00', '17:00:00'),
  (default, 1, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (default, 1, 'FRIDAY', '11:00:00', '12:00:00'),
  (default, 2, 'MONDAY', '09:00:00', '17:00:00'),
  (default, 2, 'TUESDAY', '09:00:00', '17:00:00'),
  (default, 2, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (default, 3, 'MONDAY', '09:00:00', '17:00:00'),
  (default, 3, 'TUESDAY', '09:00:00', '17:00:00'),
  (default, 3, 'WEDNESDAY', '09:00:00', '12:00:00'),
  (default, 4, 'THURSDAY', '09:00:00', '17:00:00'),
  (default, 4, 'FRIDAY', '10:00:00', '16:00:00'),
  (default, 5, 'THURSDAY', '09:00:00', '17:00:00'),
  (default, 5, 'FRIDAY', '10:00:00', '16:00:00'),
  (default, 6, 'MONDAY', '13:00:00', '14:00:00'),
  (default, 6, 'THURSDAY', '09:00:00', '17:00:00'),
  (default, 6, 'FRIDAY', '10:00:00', '16:00:00');

INSERT INTO vet_exception (id, vet_id, exception_date, unavailable) VALUES
  (default, 1, '2026-09-15', TRUE),
  (default, 5, '2026-09-15', TRUE),
  (default, 5, '2026-09-17', TRUE),
  (default, 5, '2026-09-21', TRUE),
  (default, 5, '2026-10-22', TRUE),
  (default, 6, '2026-10-22', TRUE);
