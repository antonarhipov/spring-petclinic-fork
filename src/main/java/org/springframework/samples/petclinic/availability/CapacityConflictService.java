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
import org.springframework.samples.petclinic.appointment.AppointmentCapacityBlockerSource;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.BookingState;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException.AvailabilityBlocker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CapacityConflictService {

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentRepository appointmentRepository;

	private final List<CapacityBlockerSource> blockerSources;

	private final OfferRepository offerRepository;

	private final Clock clock;

	public CapacityConflictService(EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentRepository appointmentRepository, List<CapacityBlockerSource> blockerSources,
			OfferRepository offerRepository, Clock clock) {
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.appointmentRepository = appointmentRepository;
		this.blockerSources = blockerSources;
		this.offerRepository = offerRepository;
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

	public List<TimeInterval> findVetBlockers(Integer vetId, Instant startAt, Instant endAt) {
		return this.blockerSources.stream()
			.flatMap(source -> source.findVetBlockers(vetId, startAt, endAt).stream())
			.distinct()
			.toList();
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
		return checkBookingConflicts(vetId, ownerId, petId, startAt, endAt, true, null);
	}

	public BookingConflictCheck checkStaffBookingConflicts(Integer vetId, Integer ownerId, Integer petId,
			Instant startAt, Instant endAt) {
		return checkBookingConflicts(vetId, ownerId, petId, startAt, endAt, false, null);
	}

	public BookingConflictCheck checkStaffBookingConflictsExcludingAppointment(Long appointmentId, Integer vetId,
			Integer ownerId, Integer petId, Instant startAt, Instant endAt) {
		return checkBookingConflicts(vetId, ownerId, petId, startAt, endAt, false, appointmentId);
	}

	private BookingConflictCheck checkBookingConflicts(Integer vetId, Integer ownerId, Integer petId, Instant startAt,
			Instant endAt, boolean enforceOwnerNotice, Long excludedAppointmentId) {
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
		if (!startAt.isAfter(this.clock.instant())) {
			errors.add("Appointment start time must be in the future");
		}
		if (enforceOwnerNotice
				&& startAt.isBefore(this.clock.instant().plusSeconds(policy.getOwnerNoticeMinutes() * 60L))) {
			errors.add("Appointment does not meet the owner minimum notice of " + policy.getOwnerNoticeMinutes()
					+ " minutes");
		}
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
				List<TimeInterval> vetBlockers = findVetBlockers(source, excludedAppointmentId, vetId, startAt, endAt);
				for (TimeInterval blocker : vetBlockers) {
					if (blocker.overlaps(candidateInterval)) {
						errors.add("The veterinarian has a conflicting appointment or hold from " + blocker.getStart()
								+ " to " + blocker.getEnd());
						break;
					}
				}
			}

			if (petId != null) {
				List<TimeInterval> petBlockers = findPetBlockers(source, excludedAppointmentId, petId, startAt, endAt);
				for (TimeInterval blocker : petBlockers) {
					if (blocker.overlaps(candidateInterval)) {
						errors.add("The pet has a conflicting appointment or hold from " + blocker.getStart() + " to "
								+ blocker.getEnd());
						break;
					}
				}
			}

			if (ownerId != null) {
				List<TimeInterval> ownerBlockers = findOwnerBlockers(source, excludedAppointmentId, ownerId, startAt,
						endAt);
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

	private List<TimeInterval> findVetBlockers(CapacityBlockerSource source, Long excludedAppointmentId, Integer vetId,
			Instant startAt, Instant endAt) {
		if (excludedAppointmentId != null && source instanceof AppointmentCapacityBlockerSource) {
			return this.appointmentRepository.findOverlappingByVet(vetId, BookingState.CONFIRMED, startAt, endAt)
				.stream()
				.filter(appointment -> !excludedAppointmentId.equals(appointment.getId()))
				.map(appointment -> TimeInterval.of(appointment.getStartAt(), appointment.getEndAt()))
				.toList();
		}
		return source.findVetBlockers(vetId, startAt, endAt);
	}

	private List<TimeInterval> findPetBlockers(CapacityBlockerSource source, Long excludedAppointmentId, Integer petId,
			Instant startAt, Instant endAt) {
		if (excludedAppointmentId != null && source instanceof AppointmentCapacityBlockerSource) {
			return this.appointmentRepository.findOverlappingByPet(petId, BookingState.CONFIRMED, startAt, endAt)
				.stream()
				.filter(appointment -> !excludedAppointmentId.equals(appointment.getId()))
				.map(appointment -> TimeInterval.of(appointment.getStartAt(), appointment.getEndAt()))
				.toList();
		}
		return source.findPetBlockers(petId, startAt, endAt);
	}

	private List<TimeInterval> findOwnerBlockers(CapacityBlockerSource source, Long excludedAppointmentId,
			Integer ownerId, Instant startAt, Instant endAt) {
		if (excludedAppointmentId != null && source instanceof AppointmentCapacityBlockerSource) {
			return this.appointmentRepository.findOverlappingByOwner(ownerId, BookingState.CONFIRMED, startAt, endAt)
				.stream()
				.filter(appointment -> !excludedAppointmentId.equals(appointment.getId()))
				.map(appointment -> TimeInterval.of(appointment.getStartAt(), appointment.getEndAt()))
				.toList();
		}
		return source.findOwnerBlockers(ownerId, startAt, endAt);
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

	public List<AvailabilityBlocker> findCapacityOutsideEffectiveAvailability(Integer vetId, LocalDate startDate,
			LocalDate endDate) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant rangeStart = startDate != null ? startDate.atStartOfDay(zoneId).toInstant() : this.clock.instant();
		Instant rangeEnd = endDate != null ? endDate.plusDays(1).atStartOfDay(zoneId).toInstant() : Instant.MAX;
		List<AvailabilityBlocker> blockers = new ArrayList<>();

		for (Appointment appointment : this.appointmentRepository
			.findByBookingStateAndEndAtAfter(BookingState.CONFIRMED, rangeStart)) {
			if ((vetId == null || vetId.equals(appointment.getVetId())) && appointment.getStartAt().isBefore(rangeEnd)
					&& !this.effectiveAvailabilityService.isAvailable(appointment.getVetId(), appointment.getStartAt(),
							appointment.getEndAt())) {
				blockers.add(new AvailabilityBlocker("APPOINTMENT", appointment.getId(), appointment.getOwnerId(),
						appointment.getPetId(), appointment.getVetId(), appointment.getStartAt(),
						appointment.getEndAt(), "/staff/appointments/" + appointment.getId()));
			}
		}

		for (Offer offer : this.offerRepository.findAllActiveHolds(this.clock.instant())) {
			if ((vetId == null || vetId.equals(offer.getVetId())) && offer.getEndAt().isAfter(rangeStart)
					&& offer.getStartAt().isBefore(rangeEnd) && !this.effectiveAvailabilityService
						.isAvailable(offer.getVetId(), offer.getStartAt(), offer.getEndAt())) {
				blockers.add(new AvailabilityBlocker("ACTIVE_HOLD", offer.getId(), offer.getOwnerId(), offer.getPetId(),
						offer.getVetId(), offer.getStartAt(), offer.getEndAt(),
						"/staff/queue?requestId=" + offer.getRequest().getId()));
			}
		}
		return blockers.stream()
			.sorted(java.util.Comparator.comparing(AvailabilityBlocker::startAt)
				.thenComparing(AvailabilityBlocker::type)
				.thenComparing(AvailabilityBlocker::id))
			.toList();
	}

}
