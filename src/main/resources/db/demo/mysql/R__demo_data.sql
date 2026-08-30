INSERT IGNORE INTO vets VALUES (1, 'James', 'Carter');
INSERT IGNORE INTO vets VALUES (2, 'Helen', 'Leary');
INSERT IGNORE INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT IGNORE INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT IGNORE INTO vets VALUES (5, 'Henry', 'Stevens');
INSERT IGNORE INTO vets VALUES (6, 'Sharon', 'Jenkins');

INSERT IGNORE INTO specialties VALUES (1, 'radiology');
INSERT IGNORE INTO specialties VALUES (2, 'surgery');
INSERT IGNORE INTO specialties VALUES (3, 'dentistry');

INSERT IGNORE INTO vet_specialties VALUES (2, 1);
INSERT IGNORE INTO vet_specialties VALUES (3, 2);
INSERT IGNORE INTO vet_specialties VALUES (3, 3);
INSERT IGNORE INTO vet_specialties VALUES (4, 2);
INSERT IGNORE INTO vet_specialties VALUES (5, 1);

INSERT IGNORE INTO vet_recurring_shifts (id, veterinarian_id, day_of_week, start_local_time, end_local_time) VALUES
  (1, 1, 'MONDAY', '09:00:00', '17:00:00'), (2, 1, 'TUESDAY', '09:00:00', '17:00:00'),
  (3, 1, 'WEDNESDAY', '09:00:00', '17:00:00'), (4, 1, 'THURSDAY', '09:00:00', '17:00:00'),
  (5, 1, 'FRIDAY', '09:00:00', '17:00:00'),
  (6, 2, 'MONDAY', '09:00:00', '17:00:00'), (7, 2, 'TUESDAY', '09:00:00', '17:00:00'),
  (8, 2, 'WEDNESDAY', '09:00:00', '17:00:00'), (9, 2, 'THURSDAY', '09:00:00', '17:00:00'),
  (10, 2, 'FRIDAY', '09:00:00', '17:00:00'),
  (11, 3, 'MONDAY', '09:00:00', '17:00:00'), (12, 3, 'TUESDAY', '09:00:00', '17:00:00'),
  (13, 3, 'WEDNESDAY', '09:00:00', '17:00:00'), (14, 3, 'THURSDAY', '09:00:00', '17:00:00'),
  (15, 3, 'FRIDAY', '09:00:00', '17:00:00'),
  (16, 4, 'MONDAY', '09:00:00', '17:00:00'), (17, 4, 'TUESDAY', '09:00:00', '17:00:00'),
  (18, 4, 'WEDNESDAY', '09:00:00', '17:00:00'), (19, 4, 'THURSDAY', '09:00:00', '17:00:00'),
  (20, 4, 'FRIDAY', '09:00:00', '17:00:00'),
  (21, 5, 'MONDAY', '09:00:00', '17:00:00'), (22, 5, 'TUESDAY', '09:00:00', '17:00:00'),
  (23, 5, 'WEDNESDAY', '09:00:00', '17:00:00'), (24, 5, 'THURSDAY', '09:00:00', '17:00:00'),
  (25, 5, 'FRIDAY', '09:00:00', '17:00:00'),
  (26, 6, 'MONDAY', '09:00:00', '17:00:00'), (27, 6, 'TUESDAY', '09:00:00', '17:00:00'),
  (28, 6, 'WEDNESDAY', '09:00:00', '17:00:00'), (29, 6, 'THURSDAY', '09:00:00', '17:00:00'),
  (30, 6, 'FRIDAY', '09:00:00', '17:00:00');

INSERT IGNORE INTO types VALUES (1, 'cat');
INSERT IGNORE INTO types VALUES (2, 'dog');
INSERT IGNORE INTO types VALUES (3, 'lizard');
INSERT IGNORE INTO types VALUES (4, 'snake');
INSERT IGNORE INTO types VALUES (5, 'bird');
INSERT IGNORE INTO types VALUES (6, 'hamster');

INSERT IGNORE INTO owners VALUES (1, 'George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT IGNORE INTO owners VALUES (2, 'Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT IGNORE INTO owners VALUES (3, 'Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT IGNORE INTO owners VALUES (4, 'Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT IGNORE INTO owners VALUES (5, 'Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT IGNORE INTO owners VALUES (6, 'Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT IGNORE INTO owners VALUES (7, 'Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT IGNORE INTO owners VALUES (8, 'Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT IGNORE INTO owners VALUES (9, 'David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT IGNORE INTO owners VALUES (10, 'Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');

INSERT IGNORE INTO pets VALUES (1, 'Leo', '2000-09-07', 1, 1);
INSERT IGNORE INTO pets VALUES (2, 'Basil', '2002-08-06', 6, 2);
INSERT IGNORE INTO pets VALUES (3, 'Rosy', '2001-04-17', 2, 3);
INSERT IGNORE INTO pets VALUES (4, 'Jewel', '2000-03-07', 2, 3);
INSERT IGNORE INTO pets VALUES (5, 'Iggy', '2000-11-30', 3, 4);
INSERT IGNORE INTO pets VALUES (6, 'George', '2000-01-20', 4, 5);
INSERT IGNORE INTO pets VALUES (7, 'Samantha', '1995-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (8, 'Max', '1995-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (9, 'Lucky', '1999-08-06', 5, 7);
INSERT IGNORE INTO pets VALUES (10, 'Mulligan', '1997-02-24', 2, 8);
INSERT IGNORE INTO pets VALUES (11, 'Freddy', '2000-03-09', 5, 9);
INSERT IGNORE INTO pets VALUES (12, 'Lucky', '2000-06-24', 2, 10);
INSERT IGNORE INTO pets VALUES (13, 'Sly', '2002-06-08', 1, 10);

INSERT IGNORE INTO visits (id, pet_id, visit_date, description) VALUES (1, 7, '2010-03-04', 'rabies shot');
INSERT IGNORE INTO visits (id, pet_id, visit_date, description) VALUES (2, 8, '2011-03-04', 'rabies shot');
INSERT IGNORE INTO visits (id, pet_id, visit_date, description) VALUES (3, 8, '2009-06-04', 'neutered');
INSERT IGNORE INTO visits (id, pet_id, visit_date, description) VALUES (4, 7, '2008-09-04', 'spayed');
