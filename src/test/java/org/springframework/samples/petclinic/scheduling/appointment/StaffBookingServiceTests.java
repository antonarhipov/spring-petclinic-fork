package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.ActivePetRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffBookingServiceTests {

	@Test
	void bookDirectAcquiresExactSlotAndConvertsBlocks() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		ActivePetRequestRepository activePets = mock(ActivePetRequestRepository.class);
		StaffQueueRepository queueItems = mock(StaffQueueRepository.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setPetId(1);
		request.setActiveRequestRevisionId(3L);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		Offer offer = new Offer();
		ReflectionTestUtils.setField(offer, "id", 8L);
		when(offers.findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(3L, OfferStatus.HELD))
			.thenReturn(Optional.of(offer));
		Hold hold = new Hold();
		ReflectionTestUtils.setField(hold, "id", 11L);
		when(holds.findByOfferId(8L)).thenReturn(Optional.of(hold));
		ReservationBlock block = new ReservationBlock();
		block.setHoldId(11L);
		when(blocks.findByHoldId(11L)).thenReturn(List.of(block));
		when(appointments.saveAndFlush(any())).thenAnswer(inv -> {
			Appointment appointment = inv.getArgument(0);
			ReflectionTestUtils.setField(appointment, "id", 44L);
			return appointment;
		});
		when(activePets.findByRequestId(1L)).thenReturn(Optional.empty());
		StaffQueueItem item = new StaffQueueItem();
		when(queueItems.findByRequestId(1L)).thenReturn(Optional.of(item));
		StaffBookingService service = new StaffBookingService(requests, appointments, offers, holds, blocks,
				reservations, activePets, queueItems, new BookingAuthorizationPolicy(mock(VisitRepository.class)),
				mock(AppointmentAuditService.class),
				Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC));
		CandidateSlot slot = new CandidateSlot("1@t", 1, Instant.parse("2026-03-16T15:00:00Z"),
				Instant.parse("2026-03-16T15:30:00Z"), "STAFF", 0);
		BookingAuthorization auth = new BookingAuthorization("OWNER_AGREEMENT", 2L,
				Instant.parse("2026-03-16T13:00:00Z"), "PHONE", null);
		Appointment appointment = service.bookDirect(1L, slot, auth, "OWNER_AGREEMENT");
		verify(reservations).acquireExact(1L, slot, "STAFF", "STAFF_DIRECT");
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(block.getAppointmentId()).isEqualTo(44L);
		assertThat(block.getHoldId()).isNull();
		assertThat(request.getState()).isEqualTo(RequestState.CONFIRMED);
		assertThat(item.getState()).isEqualTo(QueueState.RESOLVED);
	}

}
