package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.HoursFact;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.matching.TimeWindow;
import org.springframework.samples.petclinic.scheduling.matching.VetFact;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HoldAcquisitionServiceTests {

	@Test
	void acquireCreatesOfferAndHoldWhenEligible() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		OccupancyQueryService occupancy = mock(OccupancyQueryService.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.MATCHING);
		request.setPetId(7);
		request.setActiveRequestRevisionId(9L);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setHoldDurationMinutes(10);
		when(policies.currentPolicy()).thenReturn(policy);
		when(occupancy.activeBlocks()).thenReturn(List.of());
		when(offers.save(any())).thenAnswer(invocation -> {
			Offer offer = invocation.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(offer, "id", 44L);
			return offer;
		});
		when(holds.saveAndFlush(any())).thenAnswer(invocation -> {
			Hold hold = invocation.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(hold, "id", 55L);
			return hold;
		});
		when(blocks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ReservationService service = new ReservationService(requests, offers, holds, blocks, policies, occupancy,
				Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC));
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		CandidateSlot slot = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
		SlotSelectionSnapshot snapshot = snapshot(start, slot);
		assertThat(service.acquire(1L, UUID.randomUUID(), slot, snapshot, "EARLIEST_AVAILABLE"))
			.isEqualTo(HoldAcquisitionOutcome.HELD);
		assertThat(request.getState()).isEqualTo(RequestState.OFFER_HELD);
	}

	@Test
	void acquireReturnsStaleWhenOccupied() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		OccupancyQueryService occupancy = mock(OccupancyQueryService.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.MATCHING);
		request.setPetId(7);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		when(occupancy.activeBlocks())
			.thenReturn(List.of(new org.springframework.samples.petclinic.scheduling.matching.OccupancyFact(
					ReservationResourceType.VETERINARIAN, 1, start)));
		ReservationService service = new ReservationService(requests, offers, holds, blocks, policies, occupancy,
				Clock.systemUTC());
		CandidateSlot slot = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
		assertThat(service.acquire(1L, UUID.randomUUID(), slot, snapshot(start, slot), "EARLIEST_AVAILABLE"))
			.isEqualTo(HoldAcquisitionOutcome.STALE);
	}

	private SlotSelectionSnapshot snapshot(Instant start, CandidateSlot slot) {
		Instant now = Instant.parse("2026-03-16T14:00:00Z");
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 1L, 0, MatchingMode.PREFERRED_ONLY, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 7, null, 1, "NONE",
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), List.of(), Set.of(),
				List.of(new VetFact(1, Set.of())),
				List.of(new HoursFact(null, DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))), List.of(),
				List.of(), List.of(slot));
	}

}
