package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffCalendarQueryService {

	private final AppointmentRepository appointments;

	private final AvailabilityRepository policies;

	public StaffCalendarQueryService(AppointmentRepository appointments, AvailabilityRepository policies) {
		this.appointments = appointments;
		this.policies = policies;
	}

	@Transactional(readOnly = true)
	public List<Appointment> appointmentsOn(LocalDate date) {
		ZoneId zone = ZoneId.of(this.policies.currentPolicy().getZoneId());
		return this.appointments.findAll()
			.stream()
			.filter(appointment -> LocalDate.ofInstant(appointment.getStartAt(), zone).equals(date))
			.toList();
	}

	@Transactional(readOnly = true)
	public Appointment require(Long id) {
		return this.appointments.findById(id).orElseThrow();
	}

}
