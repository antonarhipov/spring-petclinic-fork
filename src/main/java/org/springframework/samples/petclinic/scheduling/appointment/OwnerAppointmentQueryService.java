package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.security.OwnerAccessService;

@Service
public class OwnerAppointmentQueryService {

	private final AppointmentRepository appointments;

	private final OwnerAccessService owners;

	private final Clock clock;

	public OwnerAppointmentQueryService(AppointmentRepository appointments, OwnerAccessService owners, Clock clock) {
		this.appointments = appointments;
		this.owners = owners;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Appointment> upcoming(Authentication actor) {
		return this.appointments.findUpcomingForOwner(this.owners.currentOwner(actor).owner().getId(),
				this.clock.instant());
	}

}
