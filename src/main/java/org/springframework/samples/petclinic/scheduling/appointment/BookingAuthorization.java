package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;

public record BookingAuthorization(String basis, Long agreementRecordedBy, Instant agreementAt, String agreementMethod,
		Integer supportingVisitId) {
}
