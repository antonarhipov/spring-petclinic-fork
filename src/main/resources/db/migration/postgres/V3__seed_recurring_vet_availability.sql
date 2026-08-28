INSERT INTO recurring_vet_shifts (vet_id, day_of_week, start_time, end_time, version)
SELECT vet.id, seed_day.day_of_week, CAST('09:00:00' AS TIME), CAST('17:00:00' AS TIME), 0
FROM vets vet
CROSS JOIN (
  SELECT 1 AS day_of_week
  UNION ALL SELECT 2
  UNION ALL SELECT 3
  UNION ALL SELECT 4
  UNION ALL SELECT 5
) seed_day
WHERE NOT EXISTS (
  SELECT 1
  FROM recurring_vet_shifts existing_shift
  WHERE existing_shift.vet_id = vet.id
    AND existing_shift.day_of_week = seed_day.day_of_week
);
