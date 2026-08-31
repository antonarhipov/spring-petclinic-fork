package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
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
			validatePolicy(form, existing);

			existing.setBookingHorizonDays(form.getBookingHorizonDays());
			existing.setHoldDurationMinutes(form.getHoldDurationMinutes());
			existing.setOwnerNoticeMinutes(form.getOwnerNoticeMinutes());
			existing.setStartGridMinutes(form.getStartGridMinutes());
			existing.setClinicPhone(form.getClinicPhone().trim());
			existing.setContactHours(form.getContactHours().trim());
			existing.setUrgentCareGuidance(form.getUrgentCareGuidance().trim());
			if (!form.getAllowedDurations().isEmpty()) {
				existing.setAllowedDurations(new HashSet<>(form.getAllowedDurations()));
			}
			if (!form.getOperatingIntervals().isEmpty()) {
				existing.getOperatingIntervals().clear();
				form.getOperatingIntervals()
					.forEach(
							interval -> existing.addOperatingInterval(new ClinicOperatingInterval(interval.getWeekday(),
									interval.getLocalStart(), interval.getLocalEnd())));
			}
			if (!form.getNamedPeriods().isEmpty()) {
				existing.getNamedPeriods().clear();
				form.getNamedPeriods()
					.forEach(period -> existing.addNamedPeriod(
							new NamedPeriod(period.getName().trim(), period.getLocalStart(), period.getLocalEnd())));
			}

			ClinicPolicy saved = this.clinicPolicyRepository.save(existing);
			this.clinicPolicyRepository.flush();
			ensureNoDisplacedCapacity(null, null, null,
					"Clinic policy would displace confirmed appointments or active holds.");
			this.auditService.recordEvent(actorAccountId, "UPDATE_CLINIC_POLICY", "ClinicPolicy",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);
			return saved;
		});
	}

	private void validatePolicy(ClinicPolicy form, ClinicPolicy existing) {
		if (form.getBookingHorizonDays() < 1 || form.getBookingHorizonDays() > 365) {
			throw new IllegalArgumentException("Booking horizon must be between 1 and 365 days");
		}
		if (form.getHoldDurationMinutes() < 1 || form.getHoldDurationMinutes() > 60) {
			throw new IllegalArgumentException("Hold duration must be between 1 and 60 minutes");
		}
		if (form.getOwnerNoticeMinutes() < 0) {
			throw new IllegalArgumentException("Owner notice period cannot be negative");
		}
		if (form.getStartGridMinutes() != 15) {
			throw new IllegalArgumentException("Appointment starts must remain on the fixed 15-minute grid");
		}
		requireText(form.getClinicPhone(), 32, "Clinic phone");
		requireText(form.getContactHours(), 255, "Contact hours");
		requireText(form.getUrgentCareGuidance(), 2000, "Urgent-care guidance");

		Set<Integer> durations = form.getAllowedDurations().isEmpty() ? existing.getAllowedDurations()
				: form.getAllowedDurations();
		if (durations.isEmpty() || durations.stream()
			.anyMatch(value -> value == null || value < 15 || value > 480 || value % 15 != 0)) {
			throw new IllegalArgumentException(
					"At least one permitted duration from 15 to 480 minutes on the 15-minute grid is required");
		}

		List<ClinicOperatingInterval> intervals = form.getOperatingIntervals().isEmpty()
				? existing.getOperatingIntervals() : form.getOperatingIntervals();
		if (intervals.isEmpty()) {
			throw new IllegalArgumentException("At least one clinic operating interval is required");
		}
		validateOperatingIntervals(intervals);

		List<NamedPeriod> periods = form.getNamedPeriods().isEmpty() ? existing.getNamedPeriods()
				: form.getNamedPeriods();
		validateNamedPeriods(periods);
	}

	private void validateOperatingIntervals(List<ClinicOperatingInterval> intervals) {
		for (ClinicOperatingInterval interval : intervals) {
			if (interval.getWeekday() == null
					|| !validSameDayInterval(interval.getLocalStart(), interval.getLocalEnd())) {
				throw new IllegalArgumentException(
						"Every operating interval must be a same-day 15-minute-grid interval");
			}
		}
		for (DayOfWeek weekday : DayOfWeek.values()) {
			List<ClinicOperatingInterval> dayIntervals = intervals.stream()
				.filter(interval -> interval.getWeekday() == weekday)
				.sorted(Comparator.comparing(ClinicOperatingInterval::getLocalStart))
				.toList();
			for (int index = 1; index < dayIntervals.size(); index++) {
				if (dayIntervals.get(index).getLocalStart().isBefore(dayIntervals.get(index - 1).getLocalEnd())) {
					throw new IllegalArgumentException("Clinic operating intervals cannot overlap on " + weekday);
				}
			}
		}
	}

	private void validateNamedPeriods(List<NamedPeriod> periods) {
		Set<String> names = new HashSet<>();
		List<NamedPeriod> sorted = periods.stream().sorted(Comparator.comparing(NamedPeriod::getLocalStart)).toList();
		for (NamedPeriod period : sorted) {
			requireText(period.getName(), 64, "Named period");
			if (!names.add(period.getName().trim().toLowerCase(java.util.Locale.ROOT))) {
				throw new IllegalArgumentException("Named period names must be unique");
			}
			if (!validSameDayInterval(period.getLocalStart(), period.getLocalEnd())) {
				throw new IllegalArgumentException("Every named period must be a same-day 15-minute-grid interval");
			}
		}
		for (int index = 1; index < sorted.size(); index++) {
			if (sorted.get(index).getLocalStart().isBefore(sorted.get(index - 1).getLocalEnd())) {
				throw new IllegalArgumentException("Named periods cannot overlap");
			}
		}
	}

	private boolean validSameDayInterval(LocalTime start, LocalTime end) {
		return start != null && end != null && start.isBefore(end) && start.getSecond() == 0 && end.getSecond() == 0
				&& start.getNano() == 0 && end.getNano() == 0 && start.getMinute() % 15 == 0
				&& end.getMinute() % 15 == 0;
	}

	private void requireText(String value, int maxLength, String label) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new IllegalArgumentException(label + " is required and must be at most " + maxLength + " characters");
		}
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
			RecurringShift saved = this.recurringShiftRepository.saveAndFlush(shift);
			ensureNoDisplacedCapacity(shift.getVetId(), null, null,
					"The shift change would displace confirmed appointments or active holds.");

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
			RecurringShift saved = this.recurringShiftRepository.saveAndFlush(shift);
			ensureNoDisplacedCapacity(shift.getVetId(), null, null,
					"The shift change would displace confirmed appointments or active holds.");

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
			this.recurringShiftRepository.flush();
			ensureNoDisplacedCapacity(shift.getVetId(), null, null,
					"Deleting this shift would displace confirmed appointments or active holds.");

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
			AvailabilityExceptionDay saved = this.availabilityExceptionDayRepository.saveAndFlush(exceptionDay);

			ensureNoDisplacedCapacity(vetId, date, date,
					"The exception day would displace confirmed appointments or active holds.");

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

			ensureNoDisplacedCapacity(vetId, date, date,
					"Deleting this exception day would displace confirmed appointments or active holds.");

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
			Long payloadId = null;
			if (internalNote != null && !internalNote.isBlank()) {
				UUID artifactUuid = UUID.randomUUID();
				ProtectedPayload notePayload = this.protectedPayloadService.store(artifactUuid, "VET_LEAVE_NOTE", 1,
						"text/plain", internalNote);
				payloadId = notePayload.getId();
			}

			VeterinarianLeave leave = new VeterinarianLeave(vetId, startDate, endDate, reasonCategory);
			leave.setProtectedNotePayloadId(payloadId);
			VeterinarianLeave saved = this.veterinarianLeaveRepository.saveAndFlush(leave);
			ensureNoDisplacedCapacity(vetId, startDate, endDate,
					"The leave would displace confirmed appointments or active holds.");

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
			this.veterinarianLeaveRepository.flush();
			ensureNoDisplacedCapacity(leave.getVetId(), leave.getStartDate(), leave.getEndDate(),
					"Deleting this leave would displace confirmed appointments or active holds.");

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
			Long payloadId = null;
			if (internalNote != null && !internalNote.isBlank()) {
				UUID artifactUuid = UUID.randomUUID();
				ProtectedPayload notePayload = this.protectedPayloadService.store(artifactUuid, "CLINIC_CLOSURE_NOTE",
						1, "text/plain", internalNote);
				payloadId = notePayload.getId();
			}

			ClinicClosure closure = new ClinicClosure(startDate, endDate, ownerReason);
			closure.setProtectedNotePayloadId(payloadId);
			ClinicClosure saved = this.clinicClosureRepository.saveAndFlush(closure);
			ensureNoDisplacedCapacity(null, startDate, endDate,
					"The closure would displace confirmed appointments or active holds.");

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
			this.clinicClosureRepository.flush();
			ensureNoDisplacedCapacity(null, closure.getStartDate(), closure.getEndDate(),
					"Deleting this closure would displace confirmed appointments or active holds.");

			this.auditService.recordEvent(actorAccountId, "DELETE_CLINIC_CLOSURE", "ClinicClosure",
					closureId.toString(), "SUCCESS", UUID.randomUUID(), null, null);
		});
	}

	private void ensureNoDisplacedCapacity(Integer vetId, LocalDate startDate, LocalDate endDate, String message) {
		List<AvailabilityConflictException.AvailabilityBlocker> blockers = this.capacityConflictService
			.findCapacityOutsideEffectiveAvailability(vetId, startDate, endDate);
		if (!blockers.isEmpty()) {
			throw new AvailabilityConflictException(message + " Resolve every blocking item before retrying.", blockers,
					true);
		}
	}

}
