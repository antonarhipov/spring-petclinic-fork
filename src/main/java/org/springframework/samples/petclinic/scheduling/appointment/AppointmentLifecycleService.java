package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;

@Service
public class AppointmentLifecycleService {

	private final AppointmentRepository appointments;

	private final VisitHistoryService visits;

	private final SchedulingAuditService audit;

	private final Clock clock;

	public AppointmentLifecycleService(AppointmentRepository appointments, VisitHistoryService visits,
			SchedulingAuditService audit, Clock clock) {
		this.appointments = appointments;
		this.visits = visits;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public void complete(Integer appointmentId, String notes, Authentication actor) {
		Appointment appointment = ended(appointmentId);
		appointment.changeStatus(AppointmentStatus.COMPLETED, "COMPLETED", notes);
		this.visits.complete(appointment, notes);
		this.audit.record(actor, null, AuditAction.APPOINTMENT_COMPLETED, "appointment", appointmentId, "CONFIRMED",
				"COMPLETED", null);
	}

	@Transactional
	public void noShow(Integer appointmentId, String reason, String note, Authentication actor) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A no-show reason is required");
		}
		Appointment appointment = ended(appointmentId);
		appointment.changeStatus(AppointmentStatus.NO_SHOW, reason, note);
		this.visits.removeCompletion(appointment);
		this.audit.record(actor, null, AuditAction.APPOINTMENT_NO_SHOW, "appointment", appointmentId, "CONFIRMED",
				"NO_SHOW", reason);
	}

	@Transactional
	public void correct(Integer appointmentId, AppointmentStatus status, String reason, String notes,
			Authentication actor) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
		if (appointment.getStatus() != AppointmentStatus.COMPLETED
				&& appointment.getStatus() != AppointmentStatus.NO_SHOW) {
			throw new IllegalStateException("Only completed and no-show appointments can be corrected");
		}
		appointment.changeStatus(status, reason, notes);
		if (status == AppointmentStatus.COMPLETED) {
			this.visits.complete(appointment, notes);
		}
		else {
			this.visits.removeCompletion(appointment);
		}
		this.audit.record(actor, null, AuditAction.APPOINTMENT_CORRECTED, "appointment", appointmentId, null,
				status.name(), reason);
	}

	private Appointment ended(Integer id) {
		Appointment appointment = this.appointments.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED || this.clock.instant()
			.isBefore(appointment.getStartAt().plusSeconds(appointment.getDurationMinutes() * 60L))) {
			throw new IllegalStateException("The appointment has not ended");
		}
		return appointment;
	}

}
