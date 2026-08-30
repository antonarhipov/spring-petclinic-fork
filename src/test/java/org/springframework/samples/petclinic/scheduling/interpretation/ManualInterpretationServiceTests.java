package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.queue.StaffAssistedSchedulingService;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecord;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualInterpretationServiceTests {

	@Test
	void staffInterpretationDoesNotUseLlmOrigin() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		InterpretationRecordRepository interpretations = mock(InterpretationRecordRepository.class);
		RequestRevisionRepository revisions = mock(RequestRevisionRepository.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setActiveTextRevisionId(4L);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		when(interpretations.save(any())).thenAnswer(inv -> {
			InterpretationRecord record = inv.getArgument(0);
			ReflectionTestUtils.setField(record, "id", 7L);
			return record;
		});
		when(revisions.countByRequestId(1L)).thenReturn(0);
		when(revisions.save(any())).thenAnswer(inv -> {
			RequestRevision revision = inv.getArgument(0);
			ReflectionTestUtils.setField(revision, "id", 8L);
			return revision;
		});
		ManualInterpretationService service = new ManualInterpretationService(requests, interpretations, revisions,
				mock(StaffAssistedSchedulingService.class),
				Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC));
		RequestRevision revision = service.save(1L, 2L, "Wellness", 30, "GENERAL", "NO_CONCERN_IDENTIFIED");
		assertThat(revision.getInterpretationId()).isEqualTo(7L);
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(request.getActiveRequestRevisionId()).isEqualTo(8L);
	}

}
