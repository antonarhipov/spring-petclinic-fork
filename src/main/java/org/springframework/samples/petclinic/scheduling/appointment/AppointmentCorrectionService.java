package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentCorrectionService {

	private final AppointmentRepository appointments;

	private final VisitRepository visits;

	private final OwnerRepository owners;

	private final AvailabilityRepository policies;

	private final AppointmentAuditService audit;

	private final Clock clock;

	public AppointmentCorrectionService(AppointmentRepository appointments, VisitRepository visits,
			OwnerRepository owners, AvailabilityRepository policies, AppointmentAuditService audit, Clock clock) {
		this.appointments = appointments;
		this.visits = visits;
		this.owners = owners;
		this.policies = policies;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public Appointment correct(Long appointmentId, AppointmentStatus target, Long actorAccountId, String note) {
		Appointment appointment = this.appointments.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
			throw new LifecycleException("CORRECTION_ONLY_TERMINAL");
		}
		String before = "{\"status\":\"" + appointment.getStatus() + "\"}";
		Visit existing = this.visits.findByAppointmentId(appointmentId).orElse(null);
		if (target == AppointmentStatus.COMPLETED) {
			if (existing == null) {
				Visit visit = new Visit();
				visit.setPetId(appointment.getPetId());
				visit.setAppointmentId(appointmentId);
				visit.setVetId(appointment.getVeterinarianId());
				ZoneId zone = ZoneId.of(this.policies.currentPolicy().getZoneId());
				visit.setDate(LocalDate.ofInstant(appointment.getEndAt(), zone));
				visit.setDescription(note == null ? "Corrected completion" : note);
				Owner owner = this.owners.findByPetId(appointment.getPetId()).orElseThrow();
				owner.addVisit(appointment.getPetId(), visit);
				this.owners.save(owner);
			}
			else {
				existing.setDescription(note == null ? existing.getDescription() : note);
			}
		}
		else if (existing != null) {
			Owner owner = this.owners.findByPetId(appointment.getPetId()).orElseThrow();
			Pet pet = owner.getPet(appointment.getPetId());
			if (pet != null) {
				pet.getVisits().removeIf(visit -> appointmentId.equals(visit.getAppointmentId()));
			}
			this.visits.delete(existing);
			this.owners.save(owner);
		}
		appointment.setStatus(target);
		appointment.setStaffReasonCategory(AppointmentReasonCategory.ERROR_CORRECTION.name());
		appointment.setStaffReasonNote(note);
		appointment.setUpdatedAt(Instant.now(this.clock));
		this.audit.record("STAFF", actorAccountId, "CORRECTED", appointment, before,
				"{\"status\":\"" + appointment.getStatus() + "\"}");
		return appointment;
	}

}
