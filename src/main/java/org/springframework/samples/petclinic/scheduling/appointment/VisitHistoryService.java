package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;

@Service
public class VisitHistoryService {

	private final VisitRepository visits;

	private final Clock clock;

	public VisitHistoryService(VisitRepository visits, Clock clock) {
		this.visits = visits;
		this.clock = clock;
	}

	@Transactional
	public void complete(Appointment appointment, String clinicalNotes) {
		if (this.visits.findByAppointmentId(appointment.getId()).isPresent()) {
			return;
		}
		Visit visit = new Visit();
		visit.completeFor(appointment, appointment.getVet(), this.clock.instant(), clinicalNotes);
		appointment.getPet().addVisit(visit);
	}

	@Transactional
	public void removeCompletion(Appointment appointment) {
		this.visits.findByAppointmentId(appointment.getId()).ifPresent(visit -> this.visits.delete(visit));
	}

}
