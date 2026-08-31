package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.BookingState;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffCalendarQueryService {

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentRepository appointmentRepository;

	private final VetRepository vetRepository;

	private final OwnerRepository ownerRepository;

	private final OfferRepository offerRepository;

	private final RecurringShiftRepository recurringShiftRepository;

	private final AvailabilityExceptionDayRepository exceptionDayRepository;

	private final VeterinarianLeaveRepository leaveRepository;

	private final ClinicClosureRepository closureRepository;

	public StaffCalendarQueryService(EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentRepository appointmentRepository, VetRepository vetRepository, OwnerRepository ownerRepository,
			OfferRepository offerRepository, RecurringShiftRepository recurringShiftRepository,
			AvailabilityExceptionDayRepository exceptionDayRepository, VeterinarianLeaveRepository leaveRepository,
			ClinicClosureRepository closureRepository) {
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.appointmentRepository = appointmentRepository;
		this.vetRepository = vetRepository;
		this.ownerRepository = ownerRepository;
		this.offerRepository = offerRepository;
		this.recurringShiftRepository = recurringShiftRepository;
		this.exceptionDayRepository = exceptionDayRepository;
		this.leaveRepository = leaveRepository;
		this.closureRepository = closureRepository;
	}

	public record CalendarAppointmentItem(Long id, Integer ownerId, String ownerName, Integer petId, String petName,
			Integer vetId, String vetName, ZonedDateTime start, ZonedDateTime end, String bookingState,
			String outcomeState) {
	}

	public record VetDayCalendar(Vet vet, LocalDate date, List<TimeInterval> effectiveAvailability,
			List<CalendarAppointmentItem> appointments, List<CalendarHoldItem> holds, List<String> scheduleSources) {
	}

	public record CalendarHoldItem(Long id, ZonedDateTime start, ZonedDateTime end, ZonedDateTime expiresAt) {
	}

	public record WeekCalendarView(LocalDate weekStart, LocalDate weekEnd, List<LocalDate> days, List<Vet> vets,
			Map<Integer, List<VetDayCalendar>> vetSchedules) {
	}

	public WeekCalendarView getWeekView(LocalDate referenceDate, Integer filterVetId) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate mon = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate sun = mon.plusDays(6);

		List<LocalDate> days = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			days.add(mon.plusDays(i));
		}

		List<Vet> allVets = this.vetRepository.findAll();
		List<Vet> targetVets = (filterVetId != null)
				? allVets.stream().filter(v -> v.getId().equals(filterVetId)).toList() : allVets;

		Map<Integer, List<VetDayCalendar>> schedules = new java.util.LinkedHashMap<>();
		for (Vet vet : targetVets) {
			List<VetDayCalendar> dayList = new ArrayList<>();
			for (LocalDate day : days) {
				List<TimeInterval> avail = this.effectiveAvailabilityService.getEffectiveAvailability(vet.getId(), day);
				ZonedDateTime dayStart = day.atStartOfDay(zoneId);
				ZonedDateTime dayEnd = day.plusDays(1).atStartOfDay(zoneId);

				List<Appointment> apps = this.appointmentRepository.findOverlappingByVet(vet.getId(),
						BookingState.CONFIRMED, dayStart.toInstant(), dayEnd.toInstant());

				List<CalendarAppointmentItem> appItems = new ArrayList<>();
				for (Appointment app : apps) {
					Owner owner = this.ownerRepository.findById(app.getOwnerId()).orElse(null);
					String ownerName = owner != null ? owner.getFirstName() + " " + owner.getLastName() : "Unknown";
					Pet pet = owner != null ? owner.getPet(app.getPetId()) : null;
					String petName = pet != null ? pet.getName() : "Pet #" + app.getPetId();

					appItems.add(new CalendarAppointmentItem(app.getId(), app.getOwnerId(), ownerName, app.getPetId(),
							petName, app.getVetId(), vet.getFirstName() + " " + vet.getLastName(),
							ZonedDateTime.ofInstant(app.getStartAt(), zoneId),
							ZonedDateTime.ofInstant(app.getEndAt(), zoneId), app.getBookingState().name(),
							app.getOutcomeState().name()));
				}
				List<CalendarHoldItem> holds = this.offerRepository
					.findOverlappingActiveHoldsForVet(vet.getId(), dayStart.toInstant(), dayEnd.toInstant(),
							java.time.Instant.now())
					.stream()
					.map(offer -> new CalendarHoldItem(offer.getId(),
							ZonedDateTime.ofInstant(offer.getStartAt(), zoneId),
							ZonedDateTime.ofInstant(offer.getEndAt(), zoneId),
							ZonedDateTime.ofInstant(offer.getExpiresAt(), zoneId)))
					.toList();
				List<String> sources = scheduleSources(vet.getId(), day);
				dayList.add(new VetDayCalendar(vet, day, avail, appItems, holds, sources));
			}
			schedules.put(vet.getId(), dayList);
		}

		return new WeekCalendarView(mon, sun, days, allVets, schedules);
	}

	private List<String> scheduleSources(Integer vetId, LocalDate day) {
		List<String> sources = new ArrayList<>();
		this.recurringShiftRepository.findByVetIdAndWeekday(vetId, day.getDayOfWeek())
			.forEach(shift -> sources.add("Recurring shift " + shift.getLocalStart() + "–" + shift.getLocalEnd()));
		this.exceptionDayRepository.findByVetIdAndLocalDate(vetId, day)
			.ifPresent(exception -> sources.add("Date exception (" + exception.getIntervals().size() + " intervals)"));
		this.leaveRepository.findActiveLeaveForVetOnDate(vetId, day)
			.forEach(leave -> sources.add("Leave: " + leave.getReasonCategory()));
		this.closureRepository.findActiveClosuresOnDate(day)
			.forEach(closure -> sources.add("Clinic closure: " + closure.getOwnerReason()));
		return sources;
	}

	public List<CalendarAppointmentItem> getTableAppointments(LocalDate startDate, LocalDate endDate,
			Integer filterVetId) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		ZonedDateTime startZdt = startDate.atStartOfDay(zoneId);
		ZonedDateTime endZdt = endDate.plusDays(1).atStartOfDay(zoneId);

		List<Appointment> apps;
		if (filterVetId != null) {
			apps = this.appointmentRepository.findByVetAndInterval(filterVetId, startZdt.toInstant(),
					endZdt.toInstant());
		}
		else {
			apps = this.appointmentRepository.findByInterval(startZdt.toInstant(), endZdt.toInstant());
		}

		List<CalendarAppointmentItem> items = new ArrayList<>();
		for (Appointment app : apps) {
			Owner owner = this.ownerRepository.findById(app.getOwnerId()).orElse(null);
			String ownerName = owner != null ? owner.getFirstName() + " " + owner.getLastName() : "Unknown";
			Pet pet = owner != null ? owner.getPet(app.getPetId()) : null;
			String petName = pet != null ? pet.getName() : "Pet #" + app.getPetId();
			Vet vet = this.vetRepository.findById(app.getVetId()).orElse(null);
			String vetName = vet != null ? vet.getFirstName() + " " + vet.getLastName() : "Vet #" + app.getVetId();

			items.add(new CalendarAppointmentItem(app.getId(), app.getOwnerId(), ownerName, app.getPetId(), petName,
					app.getVetId(), vetName, ZonedDateTime.ofInstant(app.getStartAt(), zoneId),
					ZonedDateTime.ofInstant(app.getEndAt(), zoneId), app.getBookingState().name(),
					app.getOutcomeState().name()));
		}
		return items;
	}

}
