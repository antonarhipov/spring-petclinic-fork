package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.audit.AuditRecord;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingEventLogQueryService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestView;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestViewQueryService;

@Service
public class StaffAppointmentQueryService {

	private final AppointmentRepository appointments;

	private final OwnerRepository owners;

	private final SchedulingRequestViewQueryService requestViews;

	private final SchedulingEventLogQueryService events;

	private final Clock clock;

	public StaffAppointmentQueryService(AppointmentRepository appointments, OwnerRepository owners,
			SchedulingRequestViewQueryService requestViews, SchedulingEventLogQueryService events, Clock clock) {
		this.appointments = appointments;
		this.owners = owners;
		this.requestViews = requestViews;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StaffAppointmentView> upcoming() {
		return this.appointments
			.findByStatusAndStartAtAfterOrderByStartAt(AppointmentStatus.CONFIRMED, this.clock.instant())
			.stream()
			.map(this::view)
			.toList();
	}

	@Transactional(readOnly = true)
	public StaffAppointmentView appointment(Integer appointmentId) {
		return view(this.appointments.findDetailedById(appointmentId).orElseThrow());
	}

	private StaffAppointmentView view(Appointment appointment) {
		Owner owner = this.owners.findByPetId(appointment.getPet().getId()).orElseThrow();
		SchedulingRequestView request = appointment.getRequest() == null ? null
				: this.requestViews.view(appointment.getRequest());
		List<AuditRecord> eventLog = this.events.forAppointment(appointment);
		return new StaffAppointmentView(appointment, owner, request, eventLog);
	}

	public record StaffAppointmentView(Appointment appointment, Owner owner, SchedulingRequestView request,
			List<AuditRecord> events) {
	}

}
