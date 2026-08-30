package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerAppointmentService {

	private final AppointmentRepository appointments;

	private final OwnerRepository owners;

	private final SchedulingRequestRepository requests;

	private final AppointmentLifecycleService lifecycle;

	private final Clock clock;

	public OwnerAppointmentService(AppointmentRepository appointments, OwnerRepository owners,
			SchedulingRequestRepository requests, AppointmentLifecycleService lifecycle, Clock clock) {
		this.appointments = appointments;
		this.owners = owners;
		this.requests = requests;
		this.lifecycle = lifecycle;
		this.clock = clock;
	}

	@Transactional
	public Appointment cancelBeforeStart(Long appointmentId, Integer ownerId, Long actorAccountId, String reason) {
		Appointment appointment = requireOwned(appointmentId, ownerId);
		if (!Instant.now(this.clock).isBefore(appointment.getStartAt())) {
			throw new LifecycleException("ALREADY_STARTED");
		}
		Appointment cancelled = this.lifecycle.cancel(appointmentId, actorAccountId, "OWNER",
				AppointmentReasonCategory.OWNER_REQUESTED, reason);
		if (appointment.getRequestId() != null) {
			SchedulingRequest request = this.requests.findById(appointment.getRequestId()).orElse(null);
			if (request != null && request.getState() != RequestState.CLOSED) {
				request.setState(RequestState.CLOSED);
				request.setOwnerStatusCode("CLOSED");
			}
		}
		return cancelled;
	}

	public Appointment requireOwned(Long appointmentId, Integer ownerId) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		Owner owner = this.owners.findById(ownerId).orElseThrow(OwnerResourceNotFoundException::new);
		if (owner.getPet(appointment.getPetId()) == null) {
			throw new OwnerResourceNotFoundException();
		}
		return appointment;
	}

}
