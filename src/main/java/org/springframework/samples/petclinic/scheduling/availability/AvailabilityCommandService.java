package org.springframework.samples.petclinic.scheduling.availability;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityCommandService {

	private final VetRecurringShiftRepository shifts;

	private final VetDateExceptionRepository exceptions;

	private final VetLeaveRepository leaves;

	private final ClinicClosureRepository closures;

	private final AvailabilityRepository policies;

	private final AvailabilityConflictGuard conflicts;

	private final ConfigurationVersionService versions;

	private final CapacityAuditService audit;

	private final HoldRepository holds;

	private final OfferRepository offers;

	private final ReservationBlockRepository blocks;

	public AvailabilityCommandService(VetRecurringShiftRepository shifts, VetDateExceptionRepository exceptions,
			VetLeaveRepository leaves, ClinicClosureRepository closures, AvailabilityRepository policies,
			AvailabilityConflictGuard conflicts, ConfigurationVersionService versions, CapacityAuditService audit,
			HoldRepository holds, OfferRepository offers, ReservationBlockRepository blocks) {
		this.shifts = shifts;
		this.exceptions = exceptions;
		this.leaves = leaves;
		this.closures = closures;
		this.policies = policies;
		this.conflicts = conflicts;
		this.versions = versions;
		this.audit = audit;
		this.holds = holds;
		this.offers = offers;
		this.blocks = blocks;
	}

	public boolean veterinarianAvailable(int veterinarianId, Instant startAt, Instant endAt) {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		ZoneId zone = ZoneId.of(policy.getZoneId());
		LocalDate date = startAt.atZone(zone).toLocalDate();
		if (!startAt.atZone(zone).toLocalDate().equals(endAt.atZone(zone).toLocalDate())) {
			return false;
		}
		for (ClinicClosure closure : this.closures.findByPolicyId(policy.getId())) {
			if (!date.isBefore(closure.getStartLocalDate()) && !date.isAfter(closure.getEndLocalDate())) {
				return false;
			}
		}
		for (VetLeave leave : this.leaves.findByVeterinarianId(veterinarianId)) {
			if (!date.isBefore(leave.getStartLocalDate()) && !date.isAfter(leave.getEndLocalDate())) {
				return false;
			}
		}
		return this.exceptions.findByVeterinarianIdAndExceptionDate(veterinarianId, date)
			.map(exception -> covered(exception.getIntervals()
				.stream()
				.map(i -> new Interval(i.getStartLocalTime(), i.getEndLocalTime()))
				.toList(), zone, startAt, endAt))
			.orElseGet(() -> covered(this.shifts.findByVeterinarianId(veterinarianId)
				.stream()
				.filter(shift -> shift.getDayOfWeek() == date.getDayOfWeek())
				.map(shift -> new Interval(shift.getStartLocalTime(), shift.getEndLocalTime()))
				.toList(), zone, startAt, endAt));
	}

	@Transactional
	public VetRecurringShift addShift(int veterinarianId, DayOfWeek day, LocalTime start, LocalTime end,
			Long actorAccountId) {
		assertGrid(start, end);
		VetRecurringShift shift = new VetRecurringShift();
		shift.setVeterinarianId(veterinarianId);
		shift.setDayOfWeek(day);
		shift.setStartLocalTime(start);
		shift.setEndLocalTime(end);
		VetRecurringShift saved = this.shifts.save(shift);
		this.versions.bump();
		this.audit.record(actorAccountId, "SHIFT_ADDED", "SHIFT", String.valueOf(saved.getId()), null, null);
		return saved;
	}

	@Transactional
	public VetDateException addDateException(int veterinarianId, LocalDate date, List<Interval> intervals,
			Long actorAccountId, boolean releaseHolds) {
		if (releaseHolds) {
			releaseOverlappingHolds(veterinarianId, date, date);
		}
		else {
			this.conflicts.assertNoConflicts(veterinarianId, date, date);
		}
		for (Interval interval : intervals) {
			assertGrid(interval.start(), interval.end());
		}
		VetDateException exception = new VetDateException();
		exception.setVeterinarianId(veterinarianId);
		exception.setExceptionDate(date);
		for (Interval interval : intervals) {
			VetDateExceptionInterval row = new VetDateExceptionInterval();
			row.setException(exception);
			row.setStartLocalTime(interval.start());
			row.setEndLocalTime(interval.end());
			exception.getIntervals().add(row);
		}
		VetDateException saved = this.exceptions.save(exception);
		this.versions.bump();
		this.audit.record(actorAccountId, "DATE_EXCEPTION_ADDED", "DATE_EXCEPTION", String.valueOf(saved.getId()), null,
				null);
		return saved;
	}

	@Transactional
	public VetLeave addLeave(int veterinarianId, LocalDate start, LocalDate end, Long actorAccountId) {
		if (end.isBefore(start)) {
			throw new PolicyValidationException("LEAVE_RANGE");
		}
		this.conflicts.assertNoConflicts(veterinarianId, start, end);
		VetLeave leave = new VetLeave();
		leave.setVeterinarianId(veterinarianId);
		leave.setStartLocalDate(start);
		leave.setEndLocalDate(end);
		VetLeave saved = this.leaves.save(leave);
		this.versions.bump();
		this.audit.record(actorAccountId, "LEAVE_ADDED", "LEAVE", String.valueOf(saved.getId()), null, null);
		return saved;
	}

	@Transactional
	public ClinicClosure addClosure(LocalDate start, LocalDate end, Long actorAccountId) {
		this.conflicts.assertNoConflicts(null, start, end);
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		ClinicClosure closure = new ClinicClosure();
		closure.setPolicyId(policy.getId());
		closure.setStartLocalDate(start);
		closure.setEndLocalDate(end);
		ClinicClosure saved = this.closures.save(closure);
		this.versions.bump();
		this.audit.record(actorAccountId, "CLOSURE_ADDED", "CLOSURE", String.valueOf(saved.getId()), null, null);
		return saved;
	}

	public List<VetRecurringShift> shifts(int veterinarianId) {
		return this.shifts.findByVeterinarianId(veterinarianId);
	}

	public List<ClinicClosure> closures() {
		return this.closures.findByPolicyId(this.policies.currentPolicy().getId());
	}

	private void releaseOverlappingHolds(Integer veterinarianId, LocalDate start, LocalDate end) {
		for (String token : this.conflicts.findConflicts(veterinarianId, start, end)) {
			if (token.startsWith("HOLD:")) {
				Long holdId = Long.valueOf(token.substring(5));
				Hold hold = this.holds.findById(holdId).orElseThrow();
				this.blocks.deleteAll(this.blocks.findByHoldId(holdId));
				hold.setState(HoldStatus.RELEASED);
				hold.setReleaseReason("CAPACITY_CHANGE");
				this.offers.findById(hold.getOfferId()).ifPresent(offer -> offer.setStatus(OfferStatus.EXPIRED));
			}
		}
	}

	private boolean covered(List<Interval> intervals, ZoneId zone, Instant startAt, Instant endAt) {
		if (intervals.isEmpty()) {
			return false;
		}
		Instant cursor = startAt;
		while (cursor.isBefore(endAt)) {
			LocalTime local = cursor.atZone(zone).toLocalTime();
			boolean open = intervals.stream()
				.anyMatch(interval -> !local.isBefore(interval.start()) && local.isBefore(interval.end()));
			if (!open) {
				return false;
			}
			cursor = cursor.plusSeconds(15 * 60);
		}
		return true;
	}

	private void assertGrid(LocalTime start, LocalTime end) {
		if (start.getMinute() % 15 != 0 || end.getMinute() % 15 != 0 || !end.isAfter(start)) {
			throw new PolicyValidationException("GRID_ALIGNMENT");
		}
	}

	public record Interval(LocalTime start, LocalTime end) {
	}

}
