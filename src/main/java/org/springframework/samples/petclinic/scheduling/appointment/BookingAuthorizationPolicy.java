package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.stereotype.Component;

@Component
public class BookingAuthorizationPolicy {

	public void validate(BookingAuthorization authorization) {
		if (authorization == null || authorization.basis() == null) {
			throw new IllegalArgumentException("AUTHORIZATION_REQUIRED");
		}
		switch (authorization.basis()) {
			case "OWNER_AGREEMENT" -> {
				if (authorization.agreementRecordedBy() == null || authorization.agreementAt() == null
						|| authorization.agreementMethod() == null || authorization.agreementMethod().isBlank()) {
					throw new IllegalArgumentException("OWNER_AGREEMENT_INCOMPLETE");
				}
			}
			case "CLINIC_FOLLOW_UP", "CLINIC_RECHECK" -> {
				if (authorization.supportingVisitId() == null) {
					throw new IllegalArgumentException("SUPPORTING_VISIT_REQUIRED");
				}
			}
			default -> throw new IllegalArgumentException("UNKNOWN_AUTHORIZATION");
		}
	}

}
