package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AvailabilityAdministrationService {

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final CalendarMutationCoordinator calendarMutationCoordinator;

	private final RecurringShiftRepository recurringShiftRepository;

	private final AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	private final VeterinarianLeaveRepository veterinarianLeaveRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final CapacityConflictService capacityConflictService;

	private final ProtectedPayloadService protectedPayloadService;

	private final AuditService auditService;

	public AvailabilityAdministrationService(ClinicPolicyRepository clinicPolicyRepository,
			CalendarMutationCoordinator calendarMutationCoordinator, RecurringShiftRepository recurringShiftRepository,
			AvailabilityExceptionDayRepository availabilityExceptionDayRepository,
			VeterinarianLeaveRepository veterinarianLeaveRepository, ClinicClosureRepository clinicClosureRepository,
			EffectiveAvailabilityService effectiveAvailabilityService, CapacityConflictService capacityConflictService,
			ProtectedPayloadService protectedPayloadService, AuditService auditService) {
		this.clinicPolicyRepository = clinicPolicyRepository;
		this.calendarMutationCoordinator = calendarMutationCoordinator;
		this.recurringShiftRepository = recurringShiftRepository;
		this.availabilityExceptionDayRepository = availabilityExceptionDayRepository;
		this.veterinarianLeaveRepository = veterinarianLeaveRepository;
		this.clinicClosureRepository = clinicClosureRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.capacityConflictService = capacityConflictService;
		this.protectedPayloadService = protectedPayloadService;
		this.auditService = auditService;
	}

	public ClinicPolicy updateClinicPolicy(ClinicPolicy form, Long actorAccountId) {
		Objects.requireNonNull(form, "form must not be null");
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			ClinicPolicy existing = this.clinicPolicyRepository.findSingleton()
				.orElseThrow(() -> new IllegalStateException("Clinic policy not initialized"));

			existing.setBookingHorizonDays(form.getBookingHorizonDays());
			existing.setHoldDurationMinutes(form.getHoldDurationMinutes());
			existing.setOwnerNoticeMinutes(form.getOwnerNoticeMinutes());
			existing.setStartGridMinutes(form.getStartGridMinutes());
			existing.setClinicPhone(form.getClinicPhone());
			existing.setContactHours(form.getContactHours());
			existing.setUrgentCareGuidance(form.getUrgentCareGuidance());

			ClinicPolicy saved = this.clinicPolicyRepository.save(existing);
			this.auditService.recordEvent(actorAccountId, "UPDATE_CLINIC_POLICY", "ClinicPolicy",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);
			return saved;
		});
	}

	public RecurringShift createRecurringShift(Integer vetId, DayOfWeek weekday, LocalTime localStart,
			LocalTime localEnd, Long actorAccountId) {
		if (localEnd.isBefore(localStart) || localEnd.equals(localStart)) {
			throw new IllegalArgumentException("Shift end must be after shift start");
		}
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			// Check for overlapping shifts for same vet and weekday
			List<RecurringShift> existingShifts = this.recurringShiftRepository.findByVetIdAndWeekday(vetId, weekday);
			LocalTimeInterval newInterval = new LocalTimeInterval(localStart, localEnd);
			for (RecurringShift existing : existingShifts) {
				LocalTimeInterval existingInterval = new LocalTimeInterval(existing.getLocalStart(),
						existing.getLocalEnd());
				if (existingInterval.overlaps(newInterval)) {
					throw new IllegalArgumentException("Shift overlaps with existing shift: " + existing.getLocalStart()
							+ "-" + existing.getLocalEnd());
				}
			}

			RecurringShift shift = new RecurringShift(vetId, weekday, localStart, localEnd);
			RecurringShift saved = this.recurringShiftRepository.save(shift);

			this.auditService.recordEvent(actorAccountId, "CREATE_RECURRING_SHIFT", "RecurringShift",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);
			return saved;
		});
	}

	public RecurringShift updateRecurringShift(Long shiftId, DayOfWeek weekday, LocalTime localStart,
			LocalTime localEnd, Long actorAccountId) {
		if (localEnd.isBefore(localStart) || localEnd.equals(localStart)) {
			throw new IllegalArgumentException("Shift end must be after shift start");
		}
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			RecurringShift shift = this.recurringShiftRepository.findById(shiftId)
				.orElseThrow(() -> new IllegalArgumentException("Recurring shift not found: " + shiftId));

			List<RecurringShift> existingShifts = this.recurringShiftRepository.findByVetIdAndWeekday(shift.getVetId(),
					weekday);
			LocalTimeInterval newInterval = new LocalTimeInterval(localStart, localEnd);
			for (RecurringShift existing : existingShifts) {
				if (!existing.getId().equals(shiftId)) {
					LocalTimeInterval existingInterval = new LocalTimeInterval(existing.getLocalStart(),
							existing.getLocalEnd());
					if (existingInterval.overlaps(newInterval)) {
						throw new IllegalArgumentException("Shift overlaps with existing shift: "
								+ existing.getLocalStart() + "-" + existing.getLocalEnd());
					}
				}
			}

			shift.setWeekday(weekday);
			shift.setLocalStart(localStart);
			shift.setLocalEnd(localEnd);
			RecurringShift saved = this.recurringShiftRepository.save(shift);

			this.auditService.recordEvent(actorAccountId, "UPDATE_RECURRING_SHIFT", "RecurringShift",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);
			return saved;
		});
	}

	public void deleteRecurringShift(Long shiftId, Long actorAccountId) {
		this.calendarMutationCoordinator.executeWithLock(() -> {
			RecurringShift shift = this.recurringShiftRepository.findById(shiftId)
				.orElseThrow(() -> new IllegalArgumentException("Recurring shift not found: " + shiftId));
			this.recurringShiftRepository.delete(shift);

			this.auditService.recordEvent(actorAccountId, "DELETE_RECURRING_SHIFT", "RecurringShift",
					shiftId.toString(), "SUCCESS", UUID.randomUUID(), null, null);
		});
	}

	public AvailabilityExceptionDay setAvailabilityExceptionDay(Integer vetId, LocalDate date,
			List<LocalTimeInterval> intervals, Long actorAccountId) {
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			AvailabilityExceptionDay exceptionDay = this.availabilityExceptionDayRepository
				.findByVetIdAndLocalDate(vetId, date)
				.orElseGet(() -> new AvailabilityExceptionDay(vetId, date));

			exceptionDay.getIntervals().clear();
			if (intervals != null) {
				for (LocalTimeInterval interval : intervals) {
					exceptionDay.addInterval(new AvailabilityExceptionInterval(interval.start(), interval.end()));
				}
			}

			// Validate whether this changes effective availability and causes conflicts
			// with existing appointments
			AvailabilityExceptionDay saved = this.availabilityExceptionDayRepository.save(exceptionDay);

			List<TimeInterval> newEffectiveAvailability = this.effectiveAvailabilityService
				.getEffectiveAvailability(vetId, date);
			List<Appointment> conflicts = this.capacityConflictService.findConflictsForNewAvailability(vetId, date,
					newEffectiveAvailability);
			if (!conflicts.isEmpty()) {
				throw new AvailabilityConflictException("Cannot update exception day: " + conflicts.size()
						+ " confirmed appointment(s) conflict with the new availability.", conflicts);
			}

			this.auditService.recordEvent(actorAccountId, "SET_AVAILABILITY_EXCEPTION_DAY", "AvailabilityExceptionDay",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);
			return saved;
		});
	}

	public void deleteAvailabilityExceptionDay(Integer vetId, LocalDate date, Long actorAccountId) {
		this.calendarMutationCoordinator.executeWithLock(() -> {
			AvailabilityExceptionDay exceptionDay = this.availabilityExceptionDayRepository
				.findByVetIdAndLocalDate(vetId, date)
				.orElseThrow(() -> new IllegalArgumentException("No exception day found for vet on " + date));

			this.availabilityExceptionDayRepository.delete(exceptionDay);
			this.availabilityExceptionDayRepository.flush();

			List<TimeInterval> newEffectiveAvailability = this.effectiveAvailabilityService
				.getEffectiveAvailability(vetId, date);
			List<Appointment> conflicts = this.capacityConflictService.findConflictsForNewAvailability(vetId, date,
					newEffectiveAvailability);
			if (!conflicts.isEmpty()) {
				throw new AvailabilityConflictException("Cannot delete exception day: " + conflicts.size()
						+ " confirmed appointment(s) conflict with recurring shifts.", conflicts);
			}

			this.auditService.recordEvent(actorAccountId, "DELETE_AVAILABILITY_EXCEPTION_DAY",
					"AvailabilityExceptionDay", exceptionDay.getId().toString(), "SUCCESS", UUID.randomUUID(), null,
					null);
		});
	}

	public VeterinarianLeave createVeterinarianLeave(Integer vetId, LocalDate startDate, LocalDate endDate,
			String reasonCategory, String internalNote, Long actorAccountId) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Leave end date must be on or after start date");
		}
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			List<Appointment> conflicts = this.capacityConflictService.findConflictsForLeave(vetId, startDate, endDate);
			if (!conflicts.isEmpty()) {
				throw new AvailabilityConflictException("Cannot schedule leave: veterinarian has " + conflicts.size()
						+ " confirmed appointment(s) during this period.", conflicts);
			}

			Long payloadId = null;
			if (internalNote != null && !internalNote.isBlank()) {
				UUID artifactUuid = UUID.randomUUID();
				ProtectedPayload notePayload = this.protectedPayloadService.store(artifactUuid, "VET_LEAVE_NOTE", 1,
						"text/plain", internalNote);
				payloadId = notePayload.getId();
			}

			VeterinarianLeave leave = new VeterinarianLeave(vetId, startDate, endDate, reasonCategory);
			leave.setProtectedNotePayloadId(payloadId);
			VeterinarianLeave saved = this.veterinarianLeaveRepository.save(leave);

			this.auditService.recordEvent(actorAccountId, "CREATE_VET_LEAVE", "VeterinarianLeave",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, payloadId);
			return saved;
		});
	}

	public void deleteVeterinarianLeave(Long leaveId, Long actorAccountId) {
		this.calendarMutationCoordinator.executeWithLock(() -> {
			VeterinarianLeave leave = this.veterinarianLeaveRepository.findById(leaveId)
				.orElseThrow(() -> new IllegalArgumentException("Veterinarian leave not found: " + leaveId));
			this.veterinarianLeaveRepository.delete(leave);

			this.auditService.recordEvent(actorAccountId, "DELETE_VET_LEAVE", "VeterinarianLeave", leaveId.toString(),
					"SUCCESS", UUID.randomUUID(), null, null);
		});
	}

	public ClinicClosure createClinicClosure(LocalDate startDate, LocalDate endDate, String ownerReason,
			String internalNote, Long actorAccountId) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Closure end date must be on or after start date");
		}
		if (ownerReason == null || ownerReason.isBlank()) {
			throw new IllegalArgumentException("Owner reason must be provided");
		}
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			List<Appointment> conflicts = this.capacityConflictService.findConflictsForClosure(startDate, endDate);
			if (!conflicts.isEmpty()) {
				throw new AvailabilityConflictException("Cannot schedule closure: clinic has " + conflicts.size()
						+ " confirmed appointment(s) during this period.", conflicts);
			}

			Long payloadId = null;
			if (internalNote != null && !internalNote.isBlank()) {
				UUID artifactUuid = UUID.randomUUID();
				ProtectedPayload notePayload = this.protectedPayloadService.store(artifactUuid, "CLINIC_CLOSURE_NOTE",
						1, "text/plain", internalNote);
				payloadId = notePayload.getId();
			}

			ClinicClosure closure = new ClinicClosure(startDate, endDate, ownerReason);
			closure.setProtectedNotePayloadId(payloadId);
			ClinicClosure saved = this.clinicClosureRepository.save(closure);

			this.auditService.recordEvent(actorAccountId, "CREATE_CLINIC_CLOSURE", "ClinicClosure",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, payloadId);
			return saved;
		});
	}

	public void deleteClinicClosure(Long closureId, Long actorAccountId) {
		this.calendarMutationCoordinator.executeWithLock(() -> {
			ClinicClosure closure = this.clinicClosureRepository.findById(closureId)
				.orElseThrow(() -> new IllegalArgumentException("Clinic closure not found: " + closureId));
			this.clinicClosureRepository.delete(closure);

			this.auditService.recordEvent(actorAccountId, "DELETE_CLINIC_CLOSURE", "ClinicClosure",
					closureId.toString(), "SUCCESS", UUID.randomUUID(), null, null);
		});
	}

}
