package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvailabilityConflictGuardTests {

	@Test
	void reportsConfirmedAppointmentsAndActiveHolds() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("UTC");
		when(policies.currentPolicy()).thenReturn(policy);
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 1L);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		when(appointments.findByStatus(AppointmentStatus.CONFIRMED)).thenReturn(List.of(appointment));
		Hold hold = new Hold();
		ReflectionTestUtils.setField(hold, "id", 2L);
		hold.setOfferId(3L);
		when(holds.findByState(HoldStatus.ACTIVE)).thenReturn(List.of(hold));
		Offer offer = new Offer();
		offer.setVeterinarianId(1);
		offer.setStartAt(Instant.parse("2026-03-16T14:00:00Z"));
		offer.setEndAt(Instant.parse("2026-03-16T14:30:00Z"));
		when(offers.findById(3L)).thenReturn(java.util.Optional.of(offer));
		AvailabilityConflictGuard guard = new AvailabilityConflictGuard(appointments, holds, offers, policies);
		assertThat(guard.findConflicts(1, LocalDate.parse("2026-03-16"), LocalDate.parse("2026-03-16")))
			.contains("APPOINTMENT:1", "HOLD:2");
	}

}
