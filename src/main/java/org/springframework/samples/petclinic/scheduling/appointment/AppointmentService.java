package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityQueryService;
import org.springframework.samples.petclinic.scheduling.offer.ReservationService;
import org.springframework.samples.petclinic.security.OwnerAccessService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@Service
public class AppointmentService {

	private final AppointmentRepository appointments;

	private final PetRepository pets;

	private final VetRepository vets;

	private final AvailabilityQueryService availability;

	private final ReservationService reservations;

	private final OwnerAccessService owners;

	private final SchedulingAuditService audit;

	private final Clock clock;

	public AppointmentService(AppointmentRepository appointments, PetRepository pets, VetRepository vets,
			AvailabilityQueryService availability, ReservationService reservations, OwnerAccessService owners,
			SchedulingAuditService audit, Clock clock) {
		this.appointments = appointments;
		this.pets = pets;
		this.vets = vets;
		this.availability = availability;
		this.reservations = reservations;
		this.owners = owners;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public Appointment directBook(Integer petId, Integer vetId, Instant startAt, int duration, AppointmentSource source,
			String agreement, String reason, Authentication actor) {
		if (source == AppointmentSource.STAFF_ASSISTED && (agreement == null || agreement.isBlank())) {
			throw new IllegalArgumentException("Recorded owner agreement is required");
		}
		if (!this.availability.isAvailable(vetId, startAt, duration)) {
			throw new IllegalStateException("The selected time is not available");
		}
		Pet pet = this.pets.findById(petId).orElseThrow(() -> new IllegalArgumentException("Pet not found"));
		Vet vet = this.vets.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Veterinarian not found"));
		Appointment appointment = this.appointments
			.save(new Appointment(pet, vet, startAt, duration, source, null, null, null, agreement));
		this.reservations.reserveConfirmed(appointment);
		this.audit.record(actor, null, AuditAction.APPOINTMENT_BOOKED, "appointment", appointment.getId(), null,
				"CONFIRMED", reason);
		return appointment;
	}

	@Transactional
	public void cancelOwned(Integer appointmentId, String reason, Authentication actor) {
		Appointment appointment = this.appointments
			.findOwnedById(appointmentId, this.owners.currentOwner(actor).owner().getId())
			.orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
		if (!appointment.getStartAt().isAfter(this.clock.instant())
				|| appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalStateException("Only an upcoming appointment can be cancelled");
		}
		appointment.changeStatus(AppointmentStatus.CANCELLED, reason, null);
		this.reservations.releaseAppointment(appointment.getId());
		this.audit.record(actor, null, AuditAction.APPOINTMENT_CANCELLED, "appointment", appointmentId, "CONFIRMED",
				"CANCELLED", reason);
	}

	@Transactional
	public void cancel(Integer appointmentId, String reason, String note, Authentication actor) {
		Appointment appointment = appointment(appointmentId);
		appointment.changeStatus(AppointmentStatus.CANCELLED, reason, note);
		this.reservations.releaseAppointment(appointmentId);
		this.audit.record(actor, null, AuditAction.APPOINTMENT_CANCELLED, "appointment", appointmentId, "CONFIRMED",
				"CANCELLED", reason);
	}

	@Transactional
	public void reschedule(Integer appointmentId, Integer vetId, Instant startAt, String reason, String note,
			Authentication actor) {
		Appointment appointment = appointment(appointmentId);
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED
				|| !this.availability.isAvailable(vetId, startAt, appointment.getDurationMinutes())) {
			throw new IllegalStateException("The appointment cannot be moved to this time");
		}
		Vet vet = this.vets.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Veterinarian not found"));
		this.reservations.releaseAppointment(appointmentId);
		appointment.reschedule(vet, startAt, reason, note);
		this.reservations.reserveConfirmed(appointment);
		this.audit.record(actor, null, AuditAction.APPOINTMENT_RESCHEDULED, "appointment", appointmentId, null,
				startAt.toString(), reason);
	}

	private Appointment appointment(Integer id) {
		return this.appointments.findById(id).orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
	}

}
