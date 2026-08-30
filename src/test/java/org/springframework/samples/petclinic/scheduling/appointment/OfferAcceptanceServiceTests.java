package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.ActivePetRequest;
import org.springframework.samples.petclinic.scheduling.request.ActivePetRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferAcceptanceServiceTests {

	@Test
	void acceptConvertsHoldToAppointmentAtomically() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		ActivePetRequestRepository activePets = mock(ActivePetRequestRepository.class);
		StaffQueueRepository queueItems = mock(StaffQueueRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.OFFER_HELD);
		request.setPetId(7);
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		Offer offer = new Offer();
		offer.setStatus(OfferStatus.HELD);
		offer.setVeterinarianId(3);
		offer.setStartAt(Instant.parse("2026-03-16T15:00:00Z"));
		offer.setEndAt(Instant.parse("2026-03-16T15:30:00Z"));
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		Hold hold = new Hold();
		hold.setState(HoldStatus.ACTIVE);
		hold.setExpiresAt(Instant.parse("2026-03-16T16:00:00Z"));
		org.springframework.test.util.ReflectionTestUtils.setField(hold, "id", 11L);
		when(holds.findByOfferId(8L)).thenReturn(Optional.of(hold));
		ReservationBlock block = new ReservationBlock();
		block.setHoldId(11L);
		when(blocks.findByHoldId(11L)).thenReturn(List.of(block));
		when(appointments.saveAndFlush(any())).thenAnswer(invocation -> {
			Appointment appointment = invocation.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(appointment, "id", 99L);
			return appointment;
		});
		when(activePets.findByRequestId(1L)).thenReturn(Optional.of(new ActivePetRequest()));
		StaffQueueItem queue = new StaffQueueItem();
		queue.setState(QueueState.NEW);
		when(queueItems.findByRequestId(1L)).thenReturn(Optional.of(queue));
		OfferAcceptanceService service = new OfferAcceptanceService(requests, offers, holds, blocks, appointments,
				activePets, queueItems, reservations,
				Clock.fixed(Instant.parse("2026-03-16T15:10:00Z"), ZoneOffset.UTC));
		Appointment appointment = service.accept(1L, 1, 8L, null);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(block.getAppointmentId()).isEqualTo(99L);
		assertThat(block.getHoldId()).isNull();
		assertThat(hold.getState()).isEqualTo(HoldStatus.CONSUMED);
		assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
		assertThat(request.getState()).isEqualTo(RequestState.CONFIRMED);
		assertThat(queue.getState()).isEqualTo(QueueState.CLOSED);
		verify(activePets).delete(any());
	}

	@Test
	void expiredAcceptDoesNotBook() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		ActivePetRequestRepository activePets = mock(ActivePetRequestRepository.class);
		StaffQueueRepository queueItems = mock(StaffQueueRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.OFFER_HELD);
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		Offer offer = new Offer();
		offer.setStatus(OfferStatus.HELD);
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		Hold hold = new Hold();
		hold.setState(HoldStatus.ACTIVE);
		hold.setExpiresAt(Instant.parse("2026-03-16T15:00:00Z"));
		when(holds.findByOfferId(8L)).thenReturn(Optional.of(hold));
		OfferAcceptanceService service = new OfferAcceptanceService(requests, offers, holds, blocks, appointments,
				activePets, queueItems, reservations,
				Clock.fixed(Instant.parse("2026-03-16T15:10:00Z"), ZoneOffset.UTC));
		assertThatThrownBy(() -> service.accept(1L, 1, 8L, null)).isInstanceOf(OfferUnavailableException.class);
	}

	@Test
	void rejectReleasesAndReturnsToReady() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		ActivePetRequestRepository activePets = mock(ActivePetRequestRepository.class);
		StaffQueueRepository queueItems = mock(StaffQueueRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.OFFER_HELD);
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		Offer offer = new Offer();
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		Hold hold = new Hold();
		when(holds.findByOfferId(8L)).thenReturn(Optional.of(hold));
		OfferAcceptanceService service = new OfferAcceptanceService(requests, offers, holds, blocks, appointments,
				activePets, queueItems, reservations, Clock.systemUTC());
		service.reject(1L, 1, 8L, null);
		verify(reservations).release(hold, offer, "REJECTED", OfferStatus.REJECTED);
		assertThat(request.getState()).isEqualTo(RequestState.READY_FOR_SUGGESTION);
	}

}
