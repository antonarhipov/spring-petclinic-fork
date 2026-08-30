package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.OccupancyFact;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentLifecycleServiceTests {

	@Test
	void rescheduleKeepsAppointmentIdAndReplacesBlocks() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		Appointment appointment = confirmed();
		when(appointments.findById(7L)).thenReturn(Optional.of(appointment));
		ReservationBlock previous = new ReservationBlock();
		previous.setResourceType(ReservationResourceType.VETERINARIAN);
		previous.setResourceId(1);
		previous.setBlockStart(Instant.parse("2026-03-16T13:00:00Z"));
		previous.setAppointmentId(7L);
		when(blocks.findByAppointmentId(7L)).thenReturn(List.of(previous));
		AvailabilityCommandService availability = mock(AvailabilityCommandService.class);
		CandidateSlot slot = new CandidateSlot("1@later", 1, Instant.parse("2026-03-16T15:00:00Z"),
				Instant.parse("2026-03-16T15:30:00Z"), "STAFF", 0);
		when(availability.veterinarianAvailable(1, slot.startAt(), slot.endAt())).thenReturn(true);
		OccupancyQueryService occupancy = mock(OccupancyQueryService.class);
		when(occupancy.activeBlocks()).thenReturn(List.of());
		AppointmentLifecycleService service = service(appointments, blocks, occupancy, availability);
		Appointment updated = service.reschedule(7L, slot, 4L, AppointmentReasonCategory.CLINIC_INITIATED, "move");
		assertThat(updated.getId()).isEqualTo(7L);
		assertThat(updated.getStartAt()).isEqualTo(slot.startAt());
		verify(blocks).deleteAll(List.of(previous));
		ArgumentCaptor<ReservationBlock> saved = ArgumentCaptor.forClass(ReservationBlock.class);
		verify(blocks, org.mockito.Mockito.times(4)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(ReservationBlock::getAppointmentId).containsOnly(7L);
		assertThat(saved.getAllValues()).extracting(ReservationBlock::getBlockStart)
			.containsExactlyInAnyOrder(Instant.parse("2026-03-16T15:00:00Z"), Instant.parse("2026-03-16T15:00:00Z"),
					Instant.parse("2026-03-16T15:15:00Z"), Instant.parse("2026-03-16T15:15:00Z"));
		assertThat(saved.getAllValues()).extracting(ReservationBlock::getResourceType)
			.containsExactlyInAnyOrder(ReservationResourceType.VETERINARIAN, ReservationResourceType.PET,
					ReservationResourceType.VETERINARIAN, ReservationResourceType.PET);
		verify(blocks).flush();
	}

	@Test
	void cancelReleasesBlocks() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		when(appointments.findById(7L)).thenReturn(Optional.of(confirmed()));
		when(blocks.findByAppointmentId(7L)).thenReturn(List.of());
		AppointmentLifecycleService service = service(appointments, blocks, mock(OccupancyQueryService.class),
				mock(AvailabilityCommandService.class));
		Appointment cancelled = service.cancel(7L, 4L, "STAFF", AppointmentReasonCategory.CLINIC_INITIATED, "closed");
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
	}

	@Test
	void completeBeforeEndIsRejected() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		Appointment appointment = confirmed();
		appointment.setEndAt(Instant.parse("2026-03-16T16:00:00Z"));
		when(appointments.findById(7L)).thenReturn(Optional.of(appointment));
		AppointmentLifecycleService service = service(appointments, mock(ReservationBlockRepository.class),
				mock(OccupancyQueryService.class), mock(AvailabilityCommandService.class));
		assertThatThrownBy(() -> service.complete(7L, 4L, "notes")).isInstanceOf(LifecycleException.class)
			.hasMessage("TOO_EARLY");
	}

	private AppointmentLifecycleService service(AppointmentRepository appointments, ReservationBlockRepository blocks,
			OccupancyQueryService occupancy, AvailabilityCommandService availability) {
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("UTC");
		when(policies.currentPolicy()).thenReturn(policy);
		return new AppointmentLifecycleService(appointments, blocks, occupancy, availability, policies,
				mock(VisitRepository.class), mock(OwnerRepository.class), mock(AppointmentAuditService.class),
				mock(StaffBookingService.class), Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC));
	}

	private Appointment confirmed() {
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 7L);
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		return appointment;
	}

}
