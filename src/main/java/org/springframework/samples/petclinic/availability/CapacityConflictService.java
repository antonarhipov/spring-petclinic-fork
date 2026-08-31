package org.springframework.samples.petclinic.availability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.BookingState;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CapacityConflictService {

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentRepository appointmentRepository;

	private final List<CapacityBlockerSource> blockerSources;

	private final Clock clock;

	public CapacityConflictService(EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentRepository appointmentRepository, List<CapacityBlockerSource> blockerSources, Clock clock) {
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.appointmentRepository = appointmentRepository;
		this.blockerSources = blockerSources;
		this.clock = clock;
	}

	public boolean hasOverlappingBlocker(Integer vetId, Integer petId, Integer ownerId, Instant startAt,
			Instant endAt) {
		if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
			return true;
		}
		TimeInterval candidate = TimeInterval.of(startAt, endAt);
		for (CapacityBlockerSource source : this.blockerSources) {
			if (vetId != null) {
				for (TimeInterval b : source.findVetBlockers(vetId, startAt, endAt)) {
					if (b.overlaps(candidate)) {
						return true;
					}
				}
			}
			if (petId != null) {
				for (TimeInterval b : source.findPetBlockers(petId, startAt, endAt)) {
					if (b.overlaps(candidate)) {
						return true;
					}
				}
			}
			if (ownerId != null) {
				for (TimeInterval b : source.findOwnerBlockers(ownerId, startAt, endAt)) {
					if (b.overlaps(candidate)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public record BookingConflictCheck(boolean valid, List<String> errorMessages) {
		public static BookingConflictCheck ok() {
			return new BookingConflictCheck(true, Collections.emptyList());
		}

		public static BookingConflictCheck failed(List<String> errors) {
			return new BookingConflictCheck(false, errors);
		}

		public static BookingConflictCheck failed(String error) {
			return new BookingConflictCheck(false, List.of(error));
		}
	}

	/**
	 * Validates a direct booking against clinic policy, effective availability, and
	 * blocker sources (vet, pet, owner).
	 */
	public BookingConflictCheck checkDirectBookingConflicts(Integer vetId, Integer ownerId, Integer petId,
			Instant startAt, Instant endAt) {
		List<String> errors = new ArrayList<>();
		if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
			errors.add("End time must be after start time");
			return BookingConflictCheck.failed(errors);
		}

		ClinicPolicy policy = this.effectiveAvailabilityService.getClinicPolicy();
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		ZonedDateTime startZdt = ZonedDateTime.ofInstant(startAt, zoneId);
		ZonedDateTime endZdt = ZonedDateTime.ofInstant(endAt, zoneId);
		LocalTime localStartTime = startZdt.toLocalTime();

		// 1. Grid alignment check
		int gridMinutes = policy.getStartGridMinutes();
		if ((localStartTime.getMinute() % gridMinutes) != 0 || localStartTime.getSecond() != 0
				|| localStartTime.getNano() != 0) {
			errors.add("Start time must be aligned to the " + gridMinutes + "-minute grid");
		}

		// 2. Duration check
		long durationMinutes = Duration.between(startAt, endAt).toMinutes();
		if (!policy.getAllowedDurations().contains((int) durationMinutes)) {
			errors.add("Duration of " + durationMinutes + " minutes is not an allowed clinic duration: "
					+ policy.getAllowedDurations());
		}

		// 3. Booking horizon check
		LocalDate bookingDate = startZdt.toLocalDate();
		LocalDate today = LocalDate.now(this.clock.withZone(zoneId));
		LocalDate maxDate = today.plusDays(policy.getBookingHorizonDays());
		if (bookingDate.isAfter(maxDate)) {
			errors.add("Appointment date " + bookingDate + " exceeds the booking horizon of "
					+ policy.getBookingHorizonDays() + " days");
		}

		// 4. Effective availability check
		if (!this.effectiveAvailabilityService.isAvailable(vetId, startAt, endAt)) {
			errors.add(
					"The veterinarian is not available at the requested time (off-shift, on leave, exception day, or clinic closure)");
		}

		TimeInterval candidateInterval = TimeInterval.of(startAt, endAt);

		// 5. Blocker checks (vet, pet, owner)
		for (CapacityBlockerSource source : this.blockerSources) {
			if (vetId != null) {
				List<TimeInterval> vetBlockers = source.findVetBlockers(vetId, startAt, endAt);
				for (TimeInterval blocker : vetBlockers) {
					if (blocker.overlaps(candidateInterval)) {
						errors.add("The veterinarian has a conflicting appointment or hold from " + blocker.getStart()
								+ " to " + blocker.getEnd());
						break;
					}
				}
			}

			if (petId != null) {
				List<TimeInterval> petBlockers = source.findPetBlockers(petId, startAt, endAt);
				for (TimeInterval blocker : petBlockers) {
					if (blocker.overlaps(candidateInterval)) {
						errors.add("The pet has a conflicting appointment or hold from " + blocker.getStart() + " to "
								+ blocker.getEnd());
						break;
					}
				}
			}

			if (ownerId != null) {
				List<TimeInterval> ownerBlockers = source.findOwnerBlockers(ownerId, startAt, endAt);
				for (TimeInterval blocker : ownerBlockers) {
					if (blocker.overlaps(candidateInterval)) {
						errors.add("The owner has a conflicting appointment or hold from " + blocker.getStart() + " to "
								+ blocker.getEnd());
						break;
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			return BookingConflictCheck.failed(errors);
		}
		return BookingConflictCheck.ok();
	}

	/**
	 * Finds any existing confirmed appointments that would conflict with adding a
	 * veterinarian leave.
	 */
	public List<Appointment> findConflictsForLeave(Integer vetId, LocalDate startDate, LocalDate endDate) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant startInstant = startDate.atStartOfDay(zoneId).toInstant();
		Instant endInstant = endDate.plusDays(1).atStartOfDay(zoneId).toInstant();
		return this.appointmentRepository.findOverlappingByVet(vetId, BookingState.CONFIRMED, startInstant, endInstant);
	}

	/**
	 * Finds any existing confirmed appointments that would conflict with adding a clinic
	 * closure.
	 */
	public List<Appointment> findConflictsForClosure(LocalDate startDate, LocalDate endDate) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant startInstant = startDate.atStartOfDay(zoneId).toInstant();
		Instant endInstant = endDate.plusDays(1).atStartOfDay(zoneId).toInstant();
		return this.appointmentRepository.findOverlappingAll(BookingState.CONFIRMED, startInstant, endInstant);
	}

	/**
	 * Finds any existing confirmed appointments for a vet on a date that fall outside the
	 * new proposed effective availability intervals.
	 */
	public List<Appointment> findConflictsForNewAvailability(Integer vetId, LocalDate date,
			List<TimeInterval> newEffectiveAvailability) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant startOfDay = date.atStartOfDay(zoneId).toInstant();
		Instant endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant();

		List<Appointment> existingAppointments = this.appointmentRepository.findOverlappingByVet(vetId,
				BookingState.CONFIRMED, startOfDay, endOfDay);

		List<Appointment> conflicting = new ArrayList<>();
		for (Appointment app : existingAppointments) {
			TimeInterval appInterval = TimeInterval.of(app.getStartAt(), app.getEndAt());
			boolean covered = false;
			for (TimeInterval avail : newEffectiveAvailability) {
				if (avail.encloses(appInterval)) {
					covered = true;
					break;
				}
			}
			if (!covered) {
				conflicting.add(app);
			}
		}
		return conflicting;
	}

}
