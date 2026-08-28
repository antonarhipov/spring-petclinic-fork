-- Candidate generation requires recurring availability. Preserve any configured shift
-- for a vet/day and fill only missing weekday schedules for existing vets.
INSERT INTO vet_weekly_shift (vet_id, day_of_week, start_local, end_local)
SELECT v.id, 1, '09:00:00', '17:00:00' FROM vets v
WHERE NOT EXISTS (SELECT 1 FROM vet_weekly_shift s WHERE s.vet_id = v.id AND s.day_of_week = 1);

INSERT INTO vet_weekly_shift (vet_id, day_of_week, start_local, end_local)
SELECT v.id, 2, '09:00:00', '17:00:00' FROM vets v
WHERE NOT EXISTS (SELECT 1 FROM vet_weekly_shift s WHERE s.vet_id = v.id AND s.day_of_week = 2);

INSERT INTO vet_weekly_shift (vet_id, day_of_week, start_local, end_local)
SELECT v.id, 3, '09:00:00', '17:00:00' FROM vets v
WHERE NOT EXISTS (SELECT 1 FROM vet_weekly_shift s WHERE s.vet_id = v.id AND s.day_of_week = 3);

INSERT INTO vet_weekly_shift (vet_id, day_of_week, start_local, end_local)
SELECT v.id, 4, '09:00:00', '17:00:00' FROM vets v
WHERE NOT EXISTS (SELECT 1 FROM vet_weekly_shift s WHERE s.vet_id = v.id AND s.day_of_week = 4);

INSERT INTO vet_weekly_shift (vet_id, day_of_week, start_local, end_local)
SELECT v.id, 5, '09:00:00', '17:00:00' FROM vets v
WHERE NOT EXISTS (SELECT 1 FROM vet_weekly_shift s WHERE s.vet_id = v.id AND s.day_of_week = 5);
