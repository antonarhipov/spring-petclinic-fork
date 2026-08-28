package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.audit.AuditRecord;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.vet.Vet;

public record SchedulingRequestView(SchedulingRequest request, String originalSourceText,
		RequestRevision interpretation, Appointment appointment, AppointmentOffer latestOffer,
		List<AuditRecord> events) {

	public Instant proposedStartAt() {
		return this.appointment != null ? this.appointment.getStartAt()
				: this.latestOffer == null ? null : this.latestOffer.getStartAt();
	}

	public Vet proposedVet() {
		return this.appointment != null ? this.appointment.getVet()
				: this.latestOffer == null ? null : this.latestOffer.getVet();
	}

}
