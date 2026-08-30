package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.queue.StaffAssistedSchedulingService;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecord;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualInterpretationService {

	private final SchedulingRequestRepository requests;

	private final InterpretationRecordRepository interpretations;

	private final RequestRevisionRepository revisions;

	private final StaffAssistedSchedulingService assisted;

	private final Clock clock;

	public ManualInterpretationService(SchedulingRequestRepository requests,
			InterpretationRecordRepository interpretations, RequestRevisionRepository revisions,
			StaffAssistedSchedulingService assisted, Clock clock) {
		this.requests = requests;
		this.interpretations = interpretations;
		this.revisions = revisions;
		this.assisted = assisted;
		this.clock = clock;
	}

	@Transactional
	public RequestRevision save(Long requestId, Long staffAccountId, String visitReason, int durationMinutes,
			String careType, String urgency) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		Instant now = Instant.now(this.clock);
		InterpretationRecord record = new InterpretationRecord();
		record.setTextRevisionId(request.getActiveTextRevisionId());
		record.setOrigin("STAFF");
		record.setSchemaVersion("1.0");
		record.setOutcome("STAFF");
		record.setCreatedByAccountId(staffAccountId);
		record.setCreatedAt(now);
		record = this.interpretations.save(record);
		RequestRevision revision = new RequestRevision();
		revision.setRequestId(requestId);
		revision.setSequence(this.revisions.countByRequestId(requestId) + 1);
		revision.setInterpretationId(record.getId());
		revision.setStatus("CONFIRMED");
		revision.setVisitReason(visitReason);
		revision.setDurationMinutes(durationMinutes);
		revision.setCareType(careType);
		revision.setUrgency(urgency);
		revision.setVeterinarianPreferenceStrength("NONE");
		revision.setClinicPolicyVersion(1);
		revision.setClinicZoneId("America/Chicago");
		revision.setConfirmedByAccountId(staffAccountId);
		revision.setConfirmedAt(now);
		revision.setCreatedAt(now);
		revision = this.revisions.save(revision);
		request.setActiveRequestRevisionId(revision.getId());
		request.setState(RequestState.STAFF_HANDLING);
		request.setUpdatedAt(now);
		return revision;
	}

}
