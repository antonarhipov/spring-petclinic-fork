package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailability;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailabilityCalculator;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class ClinicConfigurationService {

	private static final List<AppointmentStatus> PROTECTED_STATUSES = List.of(AppointmentStatus.CONFIRMED,
			AppointmentStatus.HELD);

	private final ClinicSettingsRepository settingsRepository;

	private final AppointmentRepository appointments;

	private final RequestService requests;

	private final EffectiveAvailabilityCalculator availabilityCalculator;

	private final EntityManager entityManager;

	private final Clock clock;

	public ClinicConfigurationService(ClinicSettingsRepository settingsRepository, AppointmentRepository appointments,
			RequestService requests, EffectiveAvailabilityCalculator availabilityCalculator,
			EntityManager entityManager, Clock clock) {
		this.settingsRepository = settingsRepository;
		this.appointments = appointments;
		this.requests = requests;
		this.availabilityCalculator = availabilityCalculator;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ClinicConfiguration current() {
		ClinicSettings settings = this.settingsRepository.getCurrentSettings();
		List<ClinicConfiguration.OpeningPeriod> openingHours = settings.getOpeningHours()
			.stream()
			.map(value -> new ClinicConfiguration.OpeningPeriod(value.getWeekday(), value.getOpenTime(),
					value.getCloseTime()))
			.sorted(Comparator.comparingInt(value -> value.weekday().getValue()))
			.toList();
		List<ClinicConfiguration.WorkingPeriod> workingPeriods = this.entityManager
			.createQuery("select block from VetWorkingBlock block", VetWorkingBlock.class)
			.getResultStream()
			.map(value -> new ClinicConfiguration.WorkingPeriod(value.getVet().getId(), value.getWeekday(),
					value.getStartTime(), value.getEndTime()))
			.sorted(Comparator.comparingInt(ClinicConfiguration.WorkingPeriod::veterinarianId)
				.thenComparingInt(value -> value.weekday().getValue())
				.thenComparing(ClinicConfiguration.WorkingPeriod::startTime))
			.toList();
		List<ClinicConfiguration.VetExceptionDate> exceptions = this.entityManager
			.createQuery(
					"select exception from VetException exception order by exception.vet.id, exception.exceptionDate",
					VetException.class)
			.getResultStream()
			.map(value -> new ClinicConfiguration.VetExceptionDate(value.getVet().getId(), value.getExceptionDate()))
			.toList();
		List<ClinicConfiguration.LeavePeriod> leave = this.entityManager
			.createQuery("select leave from VetLeave leave order by leave.vet.id, leave.startDate", VetLeave.class)
			.getResultStream()
			.map(value -> new ClinicConfiguration.LeavePeriod(value.getVet().getId(), value.getStartDate(),
					value.getEndDate()))
			.toList();
		List<LocalDate> closures = this.entityManager
			.createQuery("select closure.closureDate from ClinicClosure closure order by closure.closureDate",
					LocalDate.class)
			.getResultList();
		return new ClinicConfiguration(settings.getBookingHorizonDays(), settings.getMinimumLeadDays(),
				settings.getMinimumDurationMinutes(), settings.getDefaultDurationMinutes(),
				settings.getMaximumDurationMinutes(), settings.getGridMinutes(), settings.getTimeZone(), openingHours,
				workingPeriods, exceptions, leave, closures);
	}

	@Transactional(readOnly = true)
	public List<VeterinarianOption> veterinarians() {
		return this.entityManager.createQuery("select vet from Vet vet order by vet.id", Vet.class)
			.getResultStream()
			.map(vet -> new VeterinarianOption(vet.getId(), vet.getFirstName() + " " + vet.getLastName()))
			.toList();
	}

	@Transactional
	public ChangeResult change(ClinicConfiguration proposed) {
		validateStructure(proposed);
		List<Vet> lockedVets = this.entityManager.createQuery("select vet from Vet vet order by vet.id", Vet.class)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultList();
		validateVeterinarians(proposed,
				lockedVets.stream().map(Vet::getId).collect(java.util.stream.Collectors.toSet()));

		LocalDate today = LocalDate.now(this.clock);
		LocalTime now = LocalTime.now(this.clock);
		List<Appointment> conflicts = this.appointments.findProtectedFrom(today, now, PROTECTED_STATUSES)
			.stream()
			.filter(appointment -> conflictsWith(proposed, appointment))
			.toList();
		List<Conflict> confirmedConflicts = conflicts.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED)
			.map(this::toConflict)
			.toList();
		if (!confirmedConflicts.isEmpty()) {
			return ChangeResult.rejected(confirmedConflicts);
		}

		persist(proposed, lockedVets);
		List<AffectedRequest> affectedRequests = new ArrayList<>();
		for (Appointment held : conflicts) {
			if (held.getStatus() == AppointmentStatus.HELD && held.getRequest() != null) {
				affectedRequests.add(new AffectedRequest(held.getRequest().getId(), held.getPet().getName(),
						held.getVet().getFirstName() + " " + held.getVet().getLastName(), held.getDate(),
						held.getStartTime()));
				this.requests.scheduleChanged(held.getRequest().getId());
			}
		}
		return ChangeResult.saved(affectedRequests);
	}

	private boolean conflictsWith(ClinicConfiguration proposed, Appointment appointment) {
		LocalDate date = appointment.getDate();
		ClinicConfiguration.OpeningPeriod opening = proposed.openingHoursFor(date.getDayOfWeek()).orElse(null);
		List<ClinicConfiguration.WorkingPeriod> periods = proposed.workingPeriods()
			.stream()
			.filter(period -> period.weekday() == date.getDayOfWeek())
			.toList();
		Set<Integer> unavailable = new HashSet<>();
		proposed.exceptions()
			.stream()
			.filter(exception -> exception.date().equals(date))
			.map(ClinicConfiguration.VetExceptionDate::veterinarianId)
			.forEach(unavailable::add);
		proposed.leavePeriods()
			.stream()
			.filter(leave -> leave.covers(date))
			.map(ClinicConfiguration.LeavePeriod::veterinarianId)
			.forEach(unavailable::add);
		List<EffectiveAvailability> effective = this.availabilityCalculator.calculate(date, opening, periods,
				unavailable, proposed.closures().contains(date), appointment.getVet().getId());
		return effective.stream()
			.noneMatch(block -> !appointment.getStartTime().isBefore(block.startTime())
					&& !appointment.getEndTime().isAfter(block.endTime()));
	}

	private void persist(ClinicConfiguration proposed, List<Vet> lockedVets) {
		ClinicSettings settings = this.settingsRepository.getCurrentSettings();
		settings.update(proposed.bookingHorizonDays(), proposed.minimumLeadDays(), proposed.minimumDurationMinutes(),
				proposed.defaultDurationMinutes(), proposed.maximumDurationMinutes(), proposed.timeZone());
		for (OpeningHours current : settings.getOpeningHours()) {
			ClinicConfiguration.OpeningPeriod replacement = proposed.openingHoursFor(current.getWeekday())
				.orElseThrow(() -> new ConfigurationValidationException("scheduling.settings.validation.weekdays"));
			current.update(replacement.openTime(), replacement.closeTime());
		}

		this.entityManager.createQuery("delete from VetWorkingBlock").executeUpdate();
		this.entityManager.createQuery("delete from VetException").executeUpdate();
		this.entityManager.createQuery("delete from VetLeave").executeUpdate();
		this.entityManager.createQuery("delete from ClinicClosure").executeUpdate();
		java.util.Map<Integer, Vet> vetsById = lockedVets.stream()
			.collect(java.util.stream.Collectors.toMap(Vet::getId, java.util.function.Function.identity()));
		proposed.workingPeriods()
			.forEach(period -> this.entityManager.persist(new VetWorkingBlock(vetsById.get(period.veterinarianId()),
					period.weekday(), period.startTime(), period.endTime())));
		proposed.exceptions()
			.forEach(exception -> this.entityManager
				.persist(new VetException(vetsById.get(exception.veterinarianId()), exception.date())));
		proposed.leavePeriods()
			.forEach(leave -> this.entityManager
				.persist(new VetLeave(vetsById.get(leave.veterinarianId()), leave.startDate(), leave.endDate())));
		proposed.closures().forEach(date -> this.entityManager.persist(new ClinicClosure(date)));
		this.entityManager.flush();
	}

	private void validateStructure(ClinicConfiguration proposed) {
		if (proposed.bookingHorizonDays() <= 0 || proposed.minimumLeadDays() < 1) {
			throw new ConfigurationValidationException("scheduling.settings.validation.horizon");
		}
		if (proposed.minimumDurationMinutes() <= 0
				|| proposed.minimumDurationMinutes() > proposed.defaultDurationMinutes()
				|| proposed.defaultDurationMinutes() > proposed.maximumDurationMinutes()) {
			throw new ConfigurationValidationException("scheduling.settings.validation.duration");
		}
		try {
			ZoneId.of(proposed.timeZone());
		}
		catch (ZoneRulesException | NullPointerException ex) {
			throw new ConfigurationValidationException("scheduling.settings.validation.timeZone");
		}
		if (proposed.openingHours().size() != DayOfWeek.values().length || proposed.openingHours()
			.stream()
			.map(ClinicConfiguration.OpeningPeriod::weekday)
			.distinct()
			.count() != 7) {
			throw new ConfigurationValidationException("scheduling.settings.validation.weekdays");
		}
		for (ClinicConfiguration.OpeningPeriod hours : proposed.openingHours()) {
			if ((hours.openTime() == null) != (hours.closeTime() == null)
					|| (hours.isOpen() && !hours.closeTime().isAfter(hours.openTime()))) {
				throw new ConfigurationValidationException("scheduling.settings.validation.openingHours");
			}
		}
		for (ClinicConfiguration.WorkingPeriod period : proposed.workingPeriods()) {
			if (!ordered(period.startTime(), period.endTime())) {
				throw new ConfigurationValidationException("scheduling.settings.validation.workingBlocks");
			}
		}
		List<ClinicConfiguration.WorkingPeriod> sorted = proposed.workingPeriods()
			.stream()
			.sorted(Comparator.comparingInt(ClinicConfiguration.WorkingPeriod::veterinarianId)
				.thenComparing(period -> period.weekday().getValue())
				.thenComparing(ClinicConfiguration.WorkingPeriod::startTime))
			.toList();
		for (int index = 1; index < sorted.size(); index++) {
			ClinicConfiguration.WorkingPeriod previous = sorted.get(index - 1);
			ClinicConfiguration.WorkingPeriod current = sorted.get(index);
			if (previous.veterinarianId() == current.veterinarianId() && previous.weekday() == current.weekday()
					&& current.startTime().isBefore(previous.endTime())) {
				throw new ConfigurationValidationException("scheduling.settings.validation.overlap");
			}
		}
		if (proposed.exceptions()
			.stream()
			.map(value -> value.veterinarianId() + ":" + value.date())
			.collect(java.util.stream.Collectors.toSet())
			.size() != proposed.exceptions().size()
				|| new LinkedHashSet<>(proposed.closures()).size() != proposed.closures().size()) {
			throw new ConfigurationValidationException("scheduling.settings.validation.duplicates");
		}
		for (ClinicConfiguration.LeavePeriod leave : proposed.leavePeriods()) {
			if (leave.endDate().isBefore(leave.startDate())) {
				throw new ConfigurationValidationException("scheduling.settings.validation.leave");
			}
		}
	}

	private void validateVeterinarians(ClinicConfiguration proposed, Set<Integer> veterinarianIds) {
		boolean unknownWorkingVet = proposed.workingPeriods()
			.stream()
			.anyMatch(value -> !veterinarianIds.contains(value.veterinarianId()));
		boolean unknownExceptionVet = proposed.exceptions()
			.stream()
			.anyMatch(value -> !veterinarianIds.contains(value.veterinarianId()));
		boolean unknownLeaveVet = proposed.leavePeriods()
			.stream()
			.anyMatch(value -> !veterinarianIds.contains(value.veterinarianId()));
		if (unknownWorkingVet || unknownExceptionVet || unknownLeaveVet) {
			throw new ConfigurationValidationException("scheduling.settings.validation.veterinarian");
		}
	}

	private boolean ordered(LocalTime start, LocalTime end) {
		return start != null && end != null && end.isAfter(start);
	}

	private Conflict toConflict(Appointment appointment) {
		return new Conflict(appointment.getId(), appointment.getPet().getName(),
				appointment.getVet().getFirstName() + " " + appointment.getVet().getLastName(), appointment.getDate(),
				appointment.getStartTime(), appointment.getEndTime());
	}

	public record VeterinarianOption(int id, String name) {
	}

	public record Conflict(int appointmentId, String petName, String veterinarianName, LocalDate date,
			LocalTime startTime, LocalTime endTime) {
	}

	public record AffectedRequest(int requestId, String petName, String veterinarianName, LocalDate date,
			LocalTime startTime) {
	}

	public record ChangeResult(boolean saved, List<Conflict> confirmedConflicts,
			List<AffectedRequest> affectedRequests) {

		static ChangeResult rejected(List<Conflict> conflicts) {
			return new ChangeResult(false, List.copyOf(conflicts), List.of());
		}

		static ChangeResult saved(List<AffectedRequest> affectedRequests) {
			return new ChangeResult(true, List.of(), List.copyOf(affectedRequests));
		}

	}

}
