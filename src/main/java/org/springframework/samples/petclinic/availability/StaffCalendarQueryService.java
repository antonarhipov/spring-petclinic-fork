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

	public StaffCalendarQueryService(EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentRepository appointmentRepository, VetRepository vetRepository, OwnerRepository ownerRepository) {
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.appointmentRepository = appointmentRepository;
		this.vetRepository = vetRepository;
		this.ownerRepository = ownerRepository;
	}

	public record CalendarAppointmentItem(Long id, Integer ownerId, String ownerName, Integer petId, String petName,
			Integer vetId, String vetName, ZonedDateTime start, ZonedDateTime end, String bookingState,
			String outcomeState) {
	}

	public record VetDayCalendar(Vet vet, LocalDate date, List<TimeInterval> effectiveAvailability,
			List<CalendarAppointmentItem> appointments) {
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
				dayList.add(new VetDayCalendar(vet, day, avail, appItems));
			}
			schedules.put(vet.getId(), dayList);
		}

		return new WeekCalendarView(mon, sun, days, allVets, schedules);
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
