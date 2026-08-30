package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.stereotype.Component;

@Component
public class BookingAuthorizationPolicy {

	private final VisitRepository visits;

	public BookingAuthorizationPolicy(VisitRepository visits) {
		this.visits = visits;
	}

	public void validate(BookingAuthorization authorization, Integer petId) {
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
				Visit visit = this.visits.findById(authorization.supportingVisitId())
					.orElseThrow(() -> new IllegalArgumentException("SUPPORTING_VISIT_NOT_FOUND"));
				if (!visit.getPetId().equals(petId)) {
					throw new IllegalArgumentException("SUPPORTING_VISIT_WRONG_PET");
				}
				if (!visit.isDocumentedFollowUpOrRecheck()) {
					throw new IllegalArgumentException("SUPPORTING_VISIT_NOT_FOLLOW_UP");
				}
			}
			default -> throw new IllegalArgumentException("UNKNOWN_AUTHORIZATION");
		}
	}

}
