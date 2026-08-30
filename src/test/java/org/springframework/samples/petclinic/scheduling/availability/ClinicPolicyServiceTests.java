package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyTermRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicPolicyServiceTests {

	@Test
	void rejectsOutOfBoundsHorizonAndHold() {
		ClinicPolicyService service = service();
		assertThatThrownBy(() -> service.updateBounds(0, 10, 1L)).isInstanceOf(PolicyValidationException.class);
		assertThatThrownBy(() -> service.updateBounds(14, 90, 1L)).isInstanceOf(PolicyValidationException.class);
	}

	@Test
	void zoneIsImmutableOnceSchedulingDataExists() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		when(requests.count()).thenReturn(1L);
		when(appointments.count()).thenReturn(0L);
		ClinicPolicyService service = service(requests, appointments);
		assertThatThrownBy(() -> service.assertZoneImmutable("America/New_York"))
			.isInstanceOf(PolicyValidationException.class);
	}

	@Test
	void zoneIsMutableBeforeAnySchedulingDataExists() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		when(requests.count()).thenReturn(0L);
		when(appointments.count()).thenReturn(0L);
		ClinicPolicyService service = service(requests, appointments);
		ClinicSchedulingPolicy policy = service.changeZone("America/New_York", 1L);
		assertThat(policy.getZoneId()).isEqualTo("America/New_York");
	}

	@Test
	void namedPeriodsCannotOverlap() {
		NamedPeriodRepository periods = mock(NamedPeriodRepository.class);
		NamedPeriod existing = new NamedPeriod();
		existing.setCode("MORNING");
		existing.setStartLocalTime(LocalTime.of(9, 0));
		existing.setEndLocalTime(LocalTime.of(12, 0));
		when(periods.findByPolicyId(1L)).thenReturn(List.of(existing));
		ClinicPolicyService service = new ClinicPolicyService(policies(), mock(AllowedDurationRepository.class),
				mock(ClinicHoursRepository.class), periods, mock(EmergencyTermRepository.class), versions(),
				mock(CapacityAuditService.class), mock(SchedulingRequestRepository.class),
				mock(AppointmentRepository.class));
		NamedPeriod next = new NamedPeriod();
		next.setCode("MIDDAY");
		next.setStartLocalTime(LocalTime.of(11, 0));
		next.setEndLocalTime(LocalTime.of(13, 0));
		assertThatThrownBy(() -> service.saveNamedPeriod(next, 1L)).isInstanceOf(PolicyValidationException.class);
	}

	private ClinicPolicyService service() {
		return service(mock(SchedulingRequestRepository.class), mock(AppointmentRepository.class));
	}

	private ClinicPolicyService service(SchedulingRequestRepository requests, AppointmentRepository appointments) {
		return new ClinicPolicyService(policies(), mock(AllowedDurationRepository.class),
				mock(ClinicHoursRepository.class), mock(NamedPeriodRepository.class),
				mock(EmergencyTermRepository.class), versions(), mock(CapacityAuditService.class), requests,
				appointments);
	}

	private AvailabilityRepository policies() {
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		ReflectionTestUtils.setField(policy, "id", 1L);
		policy.setZoneId("UTC");
		policy.setBookingHorizonDays(14);
		policy.setHoldDurationMinutes(10);
		policy.setConfigurationVersion(1);
		when(policies.currentPolicy()).thenReturn(policy);
		when(policies.save(policy)).thenReturn(policy);
		return policies;
	}

	private ConfigurationVersionService versions() {
		return new ConfigurationVersionService(policies(), Clock.systemUTC());
	}

}
