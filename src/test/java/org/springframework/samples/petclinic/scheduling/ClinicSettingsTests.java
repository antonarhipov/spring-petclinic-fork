package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.samples.petclinic.scheduling.config.ClinicProperties;
import org.springframework.samples.petclinic.scheduling.dto.ClinicSettingsForm;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicSettingsTests {

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private AuditService auditService;

	private SchedulingTimeService timeService;

	private ClinicSettingsService settingsService;

	private final ZoneId zoneId = ZoneId.of("UTC");

	private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-27T10:07:30Z"), zoneId);

	@BeforeEach
	void setUp() {
		timeService = new SchedulingTimeService(fixedClock, zoneId);
		settingsService = new ClinicSettingsService(clinicSettingsRepository, auditService, timeService);
	}

	@Test
	void testTimeServiceNextGridBoundary() {
		Instant now = Instant.parse("2026-08-27T10:07:30Z");
		Instant next = timeService.nextGridBoundary(now);
		assertThat(next).isEqualTo(Instant.parse("2026-08-27T10:15:00Z"));

		Instant onGrid = Instant.parse("2026-08-27T10:15:00Z");
		Instant nextOnGrid = timeService.nextGridBoundary(onGrid);
		assertThat(nextOnGrid).isEqualTo(onGrid);
	}

	@Test
	void testTimeServiceHorizonCalculations() {
		Instant now = Instant.parse("2026-08-27T10:07:30Z");
		Instant ownerEnd = timeService.calculateOwnerHorizonEnd(now, 56);
		// 2026-08-27 + 56 days = 2026-10-22T00:00:00Z
		assertThat(ownerEnd).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"));

		Instant staffEnd = timeService.calculateStaffHorizonEnd(now, 365);
		// 2026-08-27 + 365 days = 2027-08-27T00:00:00Z
		assertThat(staffEnd).isEqualTo(Instant.parse("2027-08-27T00:00:00Z"));
	}

	@Test
	void testClinicPropertiesValidation() {
		ClinicProperties props = new ClinicProperties();
		props.setClinicZoneId("America/Chicago");
		props.validate();
		assertThat(props.getClinicZoneId()).isEqualTo("America/Chicago");

		ClinicProperties invalidProps = new ClinicProperties();
		invalidProps.setClinicZoneId("Invalid/ZoneName");
		assertThatThrownBy(invalidProps::validate).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void testUpdateSettingsSuccess() {
		ClinicSettings existing = new ClinicSettings();
		existing.setId(1);
		existing.setVersion(0L);

		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(existing));
		when(clinicSettingsRepository.save(any(ClinicSettings.class))).thenAnswer(inv -> inv.getArgument(0));

		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setMinDurationMinutes(15);
		form.setDefaultDurationMinutes(30);
		form.setMaxDurationMinutes(60);
		form.setOwnerBookingHorizonDays(56);
		form.setStaffBookingHorizonDays(365);
		form.setGuidedHoldDurationMinutes(5);
		form.setStaffOfferHoldDurationHours(24);
		form.setStaffClaimInactivityMinutes(30);
		form.setFallbackDeadlineDays(7);
		form.setSensitiveDataRetentionDays(30);
		form.setLockoutDurationMinutes(15);
		form.setLockoutThreshold(5);
		form.setMorningStartTime(LocalTime.of(8, 0));
		form.setMorningEndTime(LocalTime.of(12, 0));
		form.setAfternoonStartTime(LocalTime.of(12, 0));
		form.setAfternoonEndTime(LocalTime.of(17, 0));
		form.setEveningStartTime(LocalTime.of(17, 0));
		form.setEveningEndTime(LocalTime.of(20, 0));
		form.setUrgentCareGuidance("Custom urgent guidance");
		form.setEmergencyPhone("555-1234");
		form.setVersion(0L);

		ClinicSettings updated = settingsService.updateSettings(form, "admin");

		assertThat(updated.getMaxDurationMinutes()).isEqualTo(60);
		assertThat(updated.getUrgentCareGuidance()).isEqualTo("Custom urgent guidance");

		verify(auditService).recordEvent(eq(AuditEventType.SETTINGS_UPDATED), eq("admin"), eq("STAFF"),
				eq("CLINIC_SETTINGS"), eq("1"), eq("SETTINGS_UPDATED"), any());
	}

	@Test
	void testUpdateSettingsOptimisticLockingFailure() {
		ClinicSettings existing = new ClinicSettings();
		existing.setId(1);
		// Database has version 1L
		existing.setVersion(1L);

		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(existing));

		ClinicSettingsForm form = new ClinicSettingsForm();
		// Form submitted with stale version 0L
		form.setVersion(0L);

		assertThatThrownBy(() -> settingsService.updateSettings(form, "admin"))
			.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void testValidateFormInvalidDurationOrder() {
		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setMinDurationMinutes(45);
		form.setDefaultDurationMinutes(30);
		form.setMaxDurationMinutes(60);

		assertThatThrownBy(() -> settingsService.validateForm(form)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("minDuration <= defaultDuration <= maxDuration");
	}

	@Test
	void testValidateFormInvalidGrid() {
		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setMinDurationMinutes(20); // not divisible by 15

		assertThatThrownBy(() -> settingsService.validateForm(form)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("15-minute increments");
	}

	@Test
	void testValidateFormOverlappingPeriods() {
		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setMorningStartTime(LocalTime.of(8, 0));
		form.setMorningEndTime(LocalTime.of(13, 0)); // overlaps with afternoon start
														// 12:00
		form.setAfternoonStartTime(LocalTime.of(12, 0));

		assertThatThrownBy(() -> settingsService.validateForm(form)).isInstanceOf(IllegalArgumentException.class);
	}

}
