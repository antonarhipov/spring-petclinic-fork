UPDATE notifications
SET title_key = 'notification.appointmentConfirmed.title'
WHERE title_key = 'notification.appointment.confirmed.title';

UPDATE notifications
SET message_key = 'notification.appointmentConfirmed.message'
WHERE message_key = 'notification.appointment.confirmed.message';

UPDATE notifications
SET title_key = 'notification.staffOffer.title'
WHERE title_key = 'notification.staff.offer.title';

UPDATE notifications
SET message_key = 'notification.staffOffer.message'
WHERE message_key = 'notification.staff.offer.message';
