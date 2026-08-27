package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@ExtendWith(MockitoExtension.class)
class LiveFeasibilityOwnerWindowTests {

	private static final Instant NOW = Instant.parse("2026-08-27T10:33:05Z");

	@Mock
	private VetRepository vetRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Mock
	private RequestAvailabilityIntervalRepository intervalRepository;

	private LiveFeasibilityService service;

	@BeforeEach
	void setUp() {
		SchedulingTimeService timeService = new SchedulingTimeService(Clock.fixed(NOW, ZoneId.of("UTC")),
				ZoneId.of("UTC"));
		service = new LiveFeasibilityService(vetRepository, appointmentRepository, reservationRepository,
				effectiveAvailabilityService, intervalRepository, timeService);

		Vet vet = new Vet();
		vet.setId(7);
		vet.setActive(true);
		when(vetRepository.findById(7)).thenReturn(Optional.of(vet));
		when(effectiveAvailabilityService.calculateEffectiveAvailability(eq(7), any(), any()))
			.thenAnswer(invocation -> List.of(new TimeInterval(invocation.getArgument(1), invocation.getArgument(2))));
		when(appointmentRepository.findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(7),
				eq(AppointmentStatus.BOOKED), any(), any()))
			.thenReturn(List.of());
		when(appointmentRepository.findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(1),
				eq(AppointmentStatus.BOOKED), any(), any()))
			.thenReturn(List.of());
		when(appointmentRepository.findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(10),
				eq(AppointmentStatus.BOOKED), any(), any()))
			.thenReturn(List.of());
		when(reservationRepository.findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(7),
				eq(ReservationStatus.ACTIVE), any(), any()))
			.thenReturn(List.of());
		when(reservationRepository.findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(1),
				eq(ReservationStatus.ACTIVE), any(), any()))
			.thenReturn(List.of());
		when(reservationRepository.findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(eq(10),
				eq(ReservationStatus.ACTIVE), any(), any()))
			.thenReturn(List.of());
	}

	@Test
	void revalidatesAllowedAndExcludedIntervalsForRequestBackedHold() {
		when(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(100)).thenReturn(
				List.of(interval(AvailabilityWindowKind.ALLOWED, "2026-09-03T12:00:00Z", "2026-09-03T17:00:00Z"),
						interval(AvailabilityWindowKind.EXCLUDED, "2026-09-03T14:00:00Z", "2026-09-03T14:30:00Z")));

		assertThat(feasible("2026-09-03T10:45:00Z", "2026-09-03T11:15:00Z")).isFalse();
		assertThat(feasible("2026-09-03T12:00:00Z", "2026-09-03T12:30:00Z")).isTrue();
		assertThat(feasible("2026-09-03T13:45:00Z", "2026-09-03T14:15:00Z")).isFalse();
	}

	private boolean feasible(String start, String end) {
		return service.checkFeasibility(7, 1, 10, Instant.parse(start), Instant.parse(end), CareType.GENERAL, null,
				Instant.parse("2026-09-07T00:00:00Z"), 100);
	}

	private RequestAvailabilityInterval interval(AvailabilityWindowKind kind, String start, String end) {
		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setKind(kind);
		interval.setStartInstant(Instant.parse(start));
		interval.setEndInstant(Instant.parse(end));
		return interval;
	}

}
