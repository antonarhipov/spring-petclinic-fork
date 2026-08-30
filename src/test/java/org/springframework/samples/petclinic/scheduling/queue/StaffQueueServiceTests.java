package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffQueueServiceTests {

	@Test
	void emergencyItemsSortFirstThenFifo() {
		StaffQueueRepository items = mock(StaffQueueRepository.class);
		QueueNoteRepository notes = mock(QueueNoteRepository.class);
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		StaffQueueItem emergency = item(2L, "EMERGENCY", Instant.parse("2026-03-16T12:00:00Z"));
		StaffQueueItem older = item(1L, "NORMAL", Instant.parse("2026-03-16T10:00:00Z"));
		when(items.findAll()).thenReturn(List.of(older, emergency));
		when(requests.findById(1L)).thenReturn(Optional.of(request(Instant.parse("2026-03-16T10:00:00Z"))));
		when(requests.findById(2L)).thenReturn(Optional.of(request(Instant.parse("2026-03-16T12:00:00Z"))));
		StaffQueueService service = new StaffQueueService(items, notes, requests, mock(StaffQueueAuditService.class),
				Clock.fixed(Instant.parse("2026-03-16T13:00:00Z"), ZoneOffset.UTC));
		assertThat(service.openQueue()).containsExactly(emergency, older);
	}

	@Test
	void claimAndUnclaimRequireReasonOnUnclaim() {
		StaffQueueRepository items = mock(StaffQueueRepository.class);
		StaffQueueItem item = item(9L, "NORMAL", Instant.now());
		when(items.findById(9L)).thenReturn(Optional.of(item));
		StaffQueueService service = new StaffQueueService(items, mock(QueueNoteRepository.class),
				mock(SchedulingRequestRepository.class), mock(StaffQueueAuditService.class), Clock.systemUTC());
		service.claim(9L, 4L, null);
		assertThat(item.getAssigneeAccountId()).isEqualTo(4L);
		assertThat(item.getState()).isEqualTo(QueueState.IN_REVIEW);
		service.unclaim(9L, 4L, null, "handoff");
		assertThat(item.getAssigneeAccountId()).isNull();
		assertThat(item.getState()).isEqualTo(QueueState.NEW);
		assertThatThrownBy(() -> service.unclaim(9L, 4L, null, " ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void reassignRequiresReasonAndNotesStayStaffOnly() {
		StaffQueueRepository items = mock(StaffQueueRepository.class);
		QueueNoteRepository notes = mock(QueueNoteRepository.class);
		StaffQueueAuditService audit = mock(StaffQueueAuditService.class);
		StaffQueueItem item = item(9L, "NORMAL", Instant.now());
		when(items.findById(9L)).thenReturn(Optional.of(item));
		when(audit.json("assignee", 5L, "reason", "coverage")).thenReturn("{}");
		StaffQueueService service = new StaffQueueService(items, notes, mock(SchedulingRequestRepository.class), audit,
				Clock.systemUTC());
		service.reassign(9L, 4L, 5L, null, "coverage");
		assertThat(item.getAssigneeAccountId()).isEqualTo(5L);
		assertThatThrownBy(() -> service.reassign(9L, 4L, 5L, null, "")).isInstanceOf(IllegalArgumentException.class);
		service.addNote(9L, 4L, "internal");
		verify(notes).save(org.mockito.ArgumentMatchers.any());
		verify(audit).record(4L, "QUEUE_NOTE", 9L, null);
	}

	@Test
	void closeSetsResolutionAndClosesRequest() {
		StaffQueueRepository items = mock(StaffQueueRepository.class);
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		StaffQueueItem item = item(9L, "NORMAL", Instant.now());
		item.setRequestId(3L);
		when(items.findById(9L)).thenReturn(Optional.of(item));
		SchedulingRequest request = new SchedulingRequest();
		when(requests.findById(3L)).thenReturn(Optional.of(request));
		StaffQueueService service = new StaffQueueService(items, mock(QueueNoteRepository.class), requests,
				mock(StaffQueueAuditService.class), Clock.systemUTC());
		service.close(9L, 4L, null, "UNABLE_TO_CONTACT");
		assertThat(item.getState()).isEqualTo(QueueState.CLOSED);
		assertThat(request.getState()).isEqualTo(RequestState.CLOSED);
		assertThat(request.getClosureOutcome()).isEqualTo("UNABLE_TO_CONTACT");
	}

	private StaffQueueItem item(Long id, String priority, Instant created) {
		StaffQueueItem item = new StaffQueueItem();
		ReflectionTestUtils.setField(item, "id", id);
		item.setPriority(priority);
		item.setState(QueueState.NEW);
		item.setCreatedAt(created);
		item.setRequestId(id);
		return item;
	}

	private SchedulingRequest request(Instant created) {
		SchedulingRequest request = new SchedulingRequest();
		request.setCreatedAt(created);
		return request;
	}

}
