package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.scheduling.matching.StaffSlotValidator;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.StaffSlotUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffCalendarService {

	private final AppointmentRepository appointments;

	private final AppointmentService appointmentService;

	private final StaffSlotValidator slotValidator;

	public StaffCalendarService(AppointmentRepository appointments, AppointmentService appointmentService,
			StaffSlotValidator slotValidator) {
		this.appointments = appointments;
		this.appointmentService = appointmentService;
		this.slotValidator = slotValidator;
	}

	@Transactional
	public Appointment book(int petId, int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes,
			String reason, String changedBy) {
		requireReason(reason);
		validate(veterinarianId, date, startTime, durationMinutes);
		return this.appointmentService
			.tryBook(petId, veterinarianId, date, startTime, startTime.plusMinutes(durationMinutes), reason, changedBy)
			.orElseThrow(() -> new StaffSlotUnavailableException("scheduling.slot.noLongerAvailable"));
	}

	@Transactional
	public ActionResult reschedule(int appointmentId, int veterinarianId, LocalDate date, LocalTime startTime,
			String reason, String changedBy) {
		requireReason(reason);
		Appointment current = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown appointment " + appointmentId));
		int durationMinutes = current.getDurationMinutes();
		validate(veterinarianId, date, startTime, durationMinutes);
		Appointment changed = this.appointmentService
			.reschedule(appointmentId, veterinarianId, date, startTime, startTime.plusMinutes(durationMinutes), reason,
					changedBy)
			.orElseThrow(() -> new StaffSlotUnavailableException("scheduling.slot.noLongerAvailable"));
		return new ActionResult(changed, specialtyMismatch(changed));
	}

	@Transactional
	public Appointment cancel(int appointmentId, String reason, String changedBy) {
		requireReason(reason);
		return this.appointmentService.cancelByStaff(appointmentId, reason, changedBy);
	}

	@Transactional
	public Appointment complete(int appointmentId, String description) {
		return this.appointmentService.complete(appointmentId, description);
	}

	@Transactional
	public Appointment markNoShow(int appointmentId) {
		return this.appointmentService.markNoShow(appointmentId);
	}

	private void validate(int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes) {
		String refusal = this.slotValidator.refusalFor(veterinarianId, date, startTime, durationMinutes);
		if (refusal != null) {
			throw new StaffSlotUnavailableException(refusal);
		}
	}

	private boolean specialtyMismatch(Appointment appointment) {
		if (appointment.getRequest() == null) {
			return false;
		}
		Interpretation interpretation = appointment.getRequest().getCurrentInterpretation();
		if (interpretation == null || interpretation.getSpecialty() == null) {
			return false;
		}
		return appointment.getVet()
			.getSpecialties()
			.stream()
			.noneMatch(specialty -> specialty.getName().equals(interpretation.getSpecialty()));
	}

	private void requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required");
		}
	}

	public record ActionResult(Appointment appointment, boolean specialtyMismatch) {
	}

}
