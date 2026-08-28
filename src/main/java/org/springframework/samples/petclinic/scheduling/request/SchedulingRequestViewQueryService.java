package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingEventLogQueryService;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;

@Service
public class SchedulingRequestViewQueryService {

	private final RequestRevisionRepository revisions;

	private final AppointmentRepository appointments;

	private final AppointmentOfferRepository offers;

	private final SchedulingEventLogQueryService events;

	public SchedulingRequestViewQueryService(RequestRevisionRepository revisions, AppointmentRepository appointments,
			AppointmentOfferRepository offers, SchedulingEventLogQueryService events) {
		this.revisions = revisions;
		this.appointments = appointments;
		this.offers = offers;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public SchedulingRequestView view(SchedulingRequest request) {
		List<RequestRevision> allRevisions = this.revisions.findByRequestIdOrderByRevisionNumberAsc(request.getId());
		String originalSourceText = allRevisions.isEmpty() ? null : allRevisions.getFirst().getSourceText();
		RequestRevision interpretation = request.getCurrentRevision();
		Appointment appointment = this.appointments.findFirstByRequestIdOrderByIdDesc(request.getId()).orElse(null);
		AppointmentOffer latestOffer = interpretation == null ? null
				: this.offers.findFirstByRevisionIdOrderByOfferedAtDesc(interpretation.getId()).orElse(null);
		return new SchedulingRequestView(request, originalSourceText, interpretation, appointment, latestOffer,
				appointment == null ? this.events.forRequest(request) : this.events.forAppointment(appointment));
	}

}
