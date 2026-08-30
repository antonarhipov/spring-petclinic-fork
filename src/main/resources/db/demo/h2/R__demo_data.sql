INSERT INTO vets VALUES (default, 'James', 'Carter');
INSERT INTO vets VALUES (default, 'Helen', 'Leary');
INSERT INTO vets VALUES (default, 'Linda', 'Douglas');
INSERT INTO vets VALUES (default, 'Rafael', 'Ortega');
INSERT INTO vets VALUES (default, 'Henry', 'Stevens');
INSERT INTO vets VALUES (default, 'Sharon', 'Jenkins');

INSERT INTO specialties VALUES (default, 'radiology');
INSERT INTO specialties VALUES (default, 'surgery');
INSERT INTO specialties VALUES (default, 'dentistry');

INSERT INTO vet_specialties VALUES (2, 1);
INSERT INTO vet_specialties VALUES (3, 2);
INSERT INTO vet_specialties VALUES (3, 3);
INSERT INTO vet_specialties VALUES (4, 2);
INSERT INTO vet_specialties VALUES (5, 1);

INSERT INTO vet_recurring_shifts (veterinarian_id, day_of_week, start_local_time, end_local_time) VALUES
  (1, 'MONDAY', '09:00:00', '17:00:00'), (1, 'TUESDAY', '09:00:00', '17:00:00'),
  (1, 'WEDNESDAY', '09:00:00', '17:00:00'), (1, 'THURSDAY', '09:00:00', '17:00:00'),
  (1, 'FRIDAY', '09:00:00', '17:00:00'),
  (2, 'MONDAY', '09:00:00', '17:00:00'), (2, 'TUESDAY', '09:00:00', '17:00:00'),
  (2, 'WEDNESDAY', '09:00:00', '17:00:00'), (2, 'THURSDAY', '09:00:00', '17:00:00'),
  (2, 'FRIDAY', '09:00:00', '17:00:00'),
  (3, 'MONDAY', '09:00:00', '17:00:00'), (3, 'TUESDAY', '09:00:00', '17:00:00'),
  (3, 'WEDNESDAY', '09:00:00', '17:00:00'), (3, 'THURSDAY', '09:00:00', '17:00:00'),
  (3, 'FRIDAY', '09:00:00', '17:00:00'),
  (4, 'MONDAY', '09:00:00', '17:00:00'), (4, 'TUESDAY', '09:00:00', '17:00:00'),
  (4, 'WEDNESDAY', '09:00:00', '17:00:00'), (4, 'THURSDAY', '09:00:00', '17:00:00'),
  (4, 'FRIDAY', '09:00:00', '17:00:00'),
  (5, 'MONDAY', '09:00:00', '17:00:00'), (5, 'TUESDAY', '09:00:00', '17:00:00'),
  (5, 'WEDNESDAY', '09:00:00', '17:00:00'), (5, 'THURSDAY', '09:00:00', '17:00:00'),
  (5, 'FRIDAY', '09:00:00', '17:00:00'),
  (6, 'MONDAY', '09:00:00', '17:00:00'), (6, 'TUESDAY', '09:00:00', '17:00:00'),
  (6, 'WEDNESDAY', '09:00:00', '17:00:00'), (6, 'THURSDAY', '09:00:00', '17:00:00'),
  (6, 'FRIDAY', '09:00:00', '17:00:00');

INSERT INTO types VALUES (default, 'cat');
INSERT INTO types VALUES (default, 'dog');
INSERT INTO types VALUES (default, 'lizard');
INSERT INTO types VALUES (default, 'snake');
INSERT INTO types VALUES (default, 'bird');
INSERT INTO types VALUES (default, 'hamster');

INSERT INTO owners VALUES (default, 'George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT INTO owners VALUES (default, 'Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT INTO owners VALUES (default, 'Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT INTO owners VALUES (default, 'Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT INTO owners VALUES (default, 'Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT INTO owners VALUES (default, 'Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT INTO owners VALUES (default, 'Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT INTO owners VALUES (default, 'Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT INTO owners VALUES (default, 'David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT INTO owners VALUES (default, 'Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');

INSERT INTO pets VALUES (default, 'Leo', '2010-09-07', 1, 1);
INSERT INTO pets VALUES (default, 'Basil', '2012-08-06', 6, 2);
INSERT INTO pets VALUES (default, 'Rosy', '2011-04-17', 2, 3);
INSERT INTO pets VALUES (default, 'Jewel', '2010-03-07', 2, 3);
INSERT INTO pets VALUES (default, 'Iggy', '2010-11-30', 3, 4);
INSERT INTO pets VALUES (default, 'George', '2010-01-20', 4, 5);
INSERT INTO pets VALUES (default, 'Samantha', '2012-09-04', 1, 6);
INSERT INTO pets VALUES (default, 'Max', '2012-09-04', 1, 6);
INSERT INTO pets VALUES (default, 'Lucky', '2011-08-06', 5, 7);
INSERT INTO pets VALUES (default, 'Mulligan', '2007-02-24', 2, 8);
INSERT INTO pets VALUES (default, 'Freddy', '2010-03-09', 5, 9);
INSERT INTO pets VALUES (default, 'Lucky', '2010-06-24', 2, 10);
INSERT INTO pets VALUES (default, 'Sly', '2012-06-08', 1, 10);

INSERT INTO visits (pet_id, visit_date, description) VALUES (7, '2013-01-01', 'rabies shot');
INSERT INTO visits (pet_id, visit_date, description) VALUES (8, '2013-01-02', 'rabies shot');
INSERT INTO visits (pet_id, visit_date, description) VALUES (8, '2013-01-03', 'neutered');
INSERT INTO visits (pet_id, visit_date, description) VALUES (7, '2013-01-04', 'spayed');

-- Clinic vocabulary: named booking periods and emergency screening terms.
-- Both tables ship empty from V2, which leaves the staff settings screens blank and
-- forces EmergencyScreeningService onto its hardcoded fallback list.
INSERT INTO named_periods (policy_id, code, label, start_local_time, end_local_time) VALUES
  (1, 'MORNING', 'Morning', '09:00:00', '12:00:00'),
  (1, 'AFTERNOON', 'Afternoon', '12:00:00', '17:00:00'),
  (1, 'EARLY_MORNING', 'Early morning', '09:00:00', '10:30:00'),
  (1, 'LATE_AFTERNOON', 'Late afternoon', '15:00:00', '17:00:00');
INSERT INTO emergency_terms (policy_id, term, rule_set_version) VALUES
  (1, 'bleeding', 'emergency-v1'),
  (1, 'unconscious', 'emergency-v1'),
  (1, 'seizure', 'emergency-v1'),
  (1, 'poison', 'emergency-v1'),
  (1, 'collapse', 'emergency-v1');
