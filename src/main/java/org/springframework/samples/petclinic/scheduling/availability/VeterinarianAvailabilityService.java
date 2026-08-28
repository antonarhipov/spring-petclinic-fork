package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;

@Service
public class VeterinarianAvailabilityService {

	private final RecurringVetShiftRepository shifts;

	private final VetAvailabilityExceptionRepository exceptions;

	private final VetLeaveRepository leaves;

	private final ClinicClosureRepository closures;

	private final AvailabilityConflictService conflicts;

	private final SchedulingAuditService audit;

	public VeterinarianAvailabilityService(RecurringVetShiftRepository shifts,
			VetAvailabilityExceptionRepository exceptions, VetLeaveRepository leaves, ClinicClosureRepository closures,
			AvailabilityConflictService conflicts, SchedulingAuditService audit) {
		this.shifts = shifts;
		this.exceptions = exceptions;
		this.leaves = leaves;
		this.closures = closures;
		this.conflicts = conflicts;
		this.audit = audit;
	}

	@Transactional
	public RecurringVetShift addShift(Integer vetId, int day, LocalTime start, LocalTime end, Authentication actor) {
		validate(day, start, end);
		if (this.shifts.findByVetIdAndDayOfWeek(vetId, day)
			.stream()
			.anyMatch(shift -> start.isBefore(shift.getEndTime()) && end.isAfter(shift.getStartTime()))) {
			throw new IllegalArgumentException("Veterinarian shifts cannot overlap");
		}
		RecurringVetShift shift = this.shifts.save(new RecurringVetShift(vetId, day, start, end));
		this.audit.record(actor, null, AuditAction.AVAILABILITY_UPDATED, "shift", shift.getId(), null, "created", null);
		return shift;
	}

	@Transactional
	public VetAvailabilityException addException(Integer vetId, LocalDate start, LocalDate end, boolean available,
			LocalTime windowStart, LocalTime windowEnd, String reason, Authentication actor) {
		validateDates(start, end);
		if (!available) {
			this.conflicts.assertChangeAllowed(vetId, start, end);
		}
		if (windowStart != null) {
			validate(1, windowStart, windowEnd);
		}
		VetAvailabilityException exception = this.exceptions
			.save(new VetAvailabilityException(vetId, start, end, available, windowStart, windowEnd, reason));
		this.audit.record(actor, null, AuditAction.AVAILABILITY_UPDATED, "exception", exception.getId(), null,
				"created", reason);
		return exception;
	}

	@Transactional
	public VetLeave addLeave(Integer vetId, LocalDate start, LocalDate end, String reason, Authentication actor) {
		validateDates(start, end);
		this.conflicts.assertChangeAllowed(vetId, start, end);
		VetLeave leave = this.leaves.save(new VetLeave(vetId, start, end, reason));
		this.audit.record(actor, null, AuditAction.AVAILABILITY_UPDATED, "leave", leave.getId(), null, "created",
				reason);
		return leave;
	}

	@Transactional
	public ClinicClosure addClosure(LocalDate start, LocalDate end, String reason, Authentication actor) {
		validateDates(start, end);
		this.conflicts.assertClinicChangeAllowed(start, end);
		ClinicClosure closure = this.closures.save(new ClinicClosure(start, end, reason));
		this.audit.record(actor, null, AuditAction.AVAILABILITY_UPDATED, "closure", closure.getId(), null, "created",
				reason);
		return closure;
	}

	private void validate(int day, LocalTime start, LocalTime end) {
		if (day < 1 || day > 7 || start == null || end == null || !end.isAfter(start) || start.getMinute() % 15 != 0
				|| end.getMinute() % 15 != 0) {
			throw new IllegalArgumentException("Invalid 15-minute-aligned availability range");
		}
	}

	private void validateDates(LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start)) {
			throw new IllegalArgumentException("Invalid availability date range");
		}
	}

}
