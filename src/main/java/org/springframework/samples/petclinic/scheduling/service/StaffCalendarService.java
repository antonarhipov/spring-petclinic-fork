package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.dto.CalendarItemDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffCalendarService {

	private final AppointmentRepository appointmentRepository;

	private final ReservationRepository reservationRepository;

	private final SchedulingTimeService timeService;

	public StaffCalendarService(AppointmentRepository appointmentRepository,
			ReservationRepository reservationRepository, SchedulingTimeService timeService) {
		this.appointmentRepository = appointmentRepository;
		this.reservationRepository = reservationRepository;
		this.timeService = timeService;
	}

	@Transactional(readOnly = true)
	public List<CalendarItemDto> getCalendarItems(LocalDate startDate, LocalDate endDate, Integer vetId) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			return Collections.emptyList();
		}

		Instant startInstant = startDate.atStartOfDay(timeService.getClinicZoneId()).toInstant();
		Instant endInstant = endDate.plusDays(1).atStartOfDay(timeService.getClinicZoneId()).toInstant();

		List<CalendarItemDto> items = new ArrayList<>();

		// 1. Appointments
		List<Appointment> appointments = appointmentRepository.findAll()
			.stream()
			.filter(a -> vetId == null || (a.getVet() != null && a.getVet().getId().equals(vetId)))
			.filter(a -> a.getStartTime().isBefore(endInstant) && startInstant.isBefore(a.getEndTime()))
			.toList();

		for (Appointment appt : appointments) {
			items.add(new CalendarItemDto(appt.getId(), "APPOINTMENT", appt.getStatus().name(),
					appt.getVet() != null ? appt.getVet().getFirstName() + " " + appt.getVet().getLastName()
							: "Unassigned",
					appt.getOwner() != null ? appt.getOwner().getFirstName() + " " + appt.getOwner().getLastName()
							: "Unknown",
					appt.getPet() != null ? appt.getPet().getName() : "Unknown", appt.getStartTime(), appt.getEndTime(),
					appt.getCareType() != null ? appt.getCareType().name() : ""));
		}

		// 2. Active reservations (GUIDED_HOLD, STAFF_OFFER)
		List<Reservation> reservations = reservationRepository.findAll()
			.stream()
			.filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
			.filter(r -> vetId == null || (r.getVet() != null && r.getVet().getId().equals(vetId)))
			.filter(r -> r.getStartTime().isBefore(endInstant) && startInstant.isBefore(r.getEndTime()))
			.toList();

		for (Reservation res : reservations) {
			items.add(new CalendarItemDto(res.getId(), "RESERVATION", res.getReservationType().name(),
					res.getVet() != null ? res.getVet().getFirstName() + " " + res.getVet().getLastName()
							: "Unassigned",
					res.getOwner() != null ? res.getOwner().getFirstName() + " " + res.getOwner().getLastName()
							: "Unknown",
					res.getPet() != null ? res.getPet().getName() : "Unknown", res.getStartTime(), res.getEndTime(),
					"Expires: " + res.getExpiresAt()));
		}

		items.sort(Comparator.comparing(CalendarItemDto::startTime));
		return items;
	}

}
