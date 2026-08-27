package org.springframework.samples.petclinic.scheduling.service;

import java.time.LocalTime;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.samples.petclinic.scheduling.dto.ClinicSettingsForm;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicSettingsService {

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final AuditService auditService;

	private final SchedulingTimeService timeService;

	public ClinicSettingsService(ClinicSettingsRepository clinicSettingsRepository, AuditService auditService,
			SchedulingTimeService timeService) {
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.auditService = auditService;
		this.timeService = timeService;
	}

	@Transactional(readOnly = true)
	public ClinicSettings getSettings() {
		return clinicSettingsRepository.findById(1).orElseGet(() -> {
			ClinicSettings settings = new ClinicSettings();
			settings.setId(1);
			return clinicSettingsRepository.save(settings);
		});
	}

	public ClinicSettingsForm getSettingsForm() {
		ClinicSettings settings = getSettings();
		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setMinDurationMinutes(settings.getMinDurationMinutes());
		form.setMaxDurationMinutes(settings.getMaxDurationMinutes());
		form.setDefaultDurationMinutes(settings.getDefaultDurationMinutes());
		form.setOwnerBookingHorizonDays(settings.getOwnerBookingHorizonDays());
		form.setStaffBookingHorizonDays(settings.getStaffBookingHorizonDays());
		form.setGuidedHoldDurationMinutes(settings.getGuidedHoldDurationMinutes());
		form.setStaffOfferHoldDurationHours(settings.getStaffOfferHoldDurationHours());
		form.setStaffClaimInactivityMinutes(settings.getStaffClaimInactivityMinutes());
		form.setFallbackDeadlineDays(settings.getFallbackDeadlineDays());
		form.setSensitiveDataRetentionDays(settings.getSensitiveDataRetentionDays());
		form.setLockoutDurationMinutes(settings.getLockoutDurationMinutes());
		form.setLockoutThreshold(settings.getLockoutThreshold());
		form.setMorningStartTime(settings.getMorningStartTime());
		form.setMorningEndTime(settings.getMorningEndTime());
		form.setAfternoonStartTime(settings.getAfternoonStartTime());
		form.setAfternoonEndTime(settings.getAfternoonEndTime());
		form.setEveningStartTime(settings.getEveningStartTime());
		form.setEveningEndTime(settings.getEveningEndTime());
		form.setUrgentCareGuidance(settings.getUrgentCareGuidance());
		form.setEmergencyPhone(settings.getEmergencyPhone());
		form.setVersion(settings.getVersion());
		return form;
	}

	@Transactional
	public ClinicSettings updateSettings(ClinicSettingsForm form, String actorUsername) {
		validateForm(form);

		ClinicSettings settings = clinicSettingsRepository.findById(1)
			.orElseThrow(() -> new IllegalStateException("Clinic settings not found"));

		if (form.getVersion() != null && !form.getVersion().equals(settings.getVersion())) {
			throw new OptimisticLockingFailureException(
					"Settings have been modified by another transaction. Please reload and try again.");
		}

		settings.setMinDurationMinutes(form.getMinDurationMinutes());
		settings.setMaxDurationMinutes(form.getMaxDurationMinutes());
		settings.setDefaultDurationMinutes(form.getDefaultDurationMinutes());
		settings.setOwnerBookingHorizonDays(form.getOwnerBookingHorizonDays());
		settings.setStaffBookingHorizonDays(form.getStaffBookingHorizonDays());
		settings.setGuidedHoldDurationMinutes(form.getGuidedHoldDurationMinutes());
		settings.setStaffOfferHoldDurationHours(form.getStaffOfferHoldDurationHours());
		settings.setStaffClaimInactivityMinutes(form.getStaffClaimInactivityMinutes());
		settings.setFallbackDeadlineDays(form.getFallbackDeadlineDays());
		settings.setSensitiveDataRetentionDays(form.getSensitiveDataRetentionDays());
		settings.setLockoutDurationMinutes(form.getLockoutDurationMinutes());
		settings.setLockoutThreshold(form.getLockoutThreshold());
		settings.setMorningStartTime(form.getMorningStartTime());
		settings.setMorningEndTime(form.getMorningEndTime());
		settings.setAfternoonStartTime(form.getAfternoonStartTime());
		settings.setAfternoonEndTime(form.getAfternoonEndTime());
		settings.setEveningStartTime(form.getEveningStartTime());
		settings.setEveningEndTime(form.getEveningEndTime());
		settings.setUrgentCareGuidance(form.getUrgentCareGuidance().trim());
		settings.setEmergencyPhone(form.getEmergencyPhone().trim());

		ClinicSettings saved = clinicSettingsRepository.save(settings);

		auditService.recordEvent(AuditEventType.SETTINGS_UPDATED, actorUsername != null ? actorUsername : "system",
				"STAFF", "CLINIC_SETTINGS", "1", "SETTINGS_UPDATED",
				Map.of("version", String.valueOf(saved.getVersion())));

		return saved;
	}

	public void validateForm(ClinicSettingsForm form) {
		if (form.getMinDurationMinutes() < 15 || form.getMinDurationMinutes() > 120
				|| form.getMinDurationMinutes() % 15 != 0) {
			throw new IllegalArgumentException(
					"Min duration must be between 15 and 120 minutes in 15-minute increments");
		}
		if (form.getMaxDurationMinutes() < 15 || form.getMaxDurationMinutes() > 120
				|| form.getMaxDurationMinutes() % 15 != 0) {
			throw new IllegalArgumentException(
					"Max duration must be between 15 and 120 minutes in 15-minute increments");
		}
		if (form.getDefaultDurationMinutes() < 15 || form.getDefaultDurationMinutes() > 120
				|| form.getDefaultDurationMinutes() % 15 != 0) {
			throw new IllegalArgumentException(
					"Default duration must be between 15 and 120 minutes in 15-minute increments");
		}
		if (form.getMinDurationMinutes() > form.getDefaultDurationMinutes()
				|| form.getDefaultDurationMinutes() > form.getMaxDurationMinutes()) {
			throw new IllegalArgumentException("Durations must satisfy: minDuration <= defaultDuration <= maxDuration");
		}
		if (form.getOwnerBookingHorizonDays() <= 0) {
			throw new IllegalArgumentException("Owner booking horizon days must be a positive integer");
		}
		if (form.getStaffBookingHorizonDays() <= 0 || form.getStaffBookingHorizonDays() > 730) {
			throw new IllegalArgumentException("Staff booking horizon days must be between 1 and 730");
		}
		if (form.getGuidedHoldDurationMinutes() <= 0) {
			throw new IllegalArgumentException("Guided hold duration minutes must be a positive integer");
		}
		if (form.getStaffOfferHoldDurationHours() <= 0) {
			throw new IllegalArgumentException("Staff offer hold duration hours must be a positive integer");
		}
		if (form.getStaffClaimInactivityMinutes() <= 0) {
			throw new IllegalArgumentException("Staff claim inactivity minutes must be a positive integer");
		}
		if (form.getFallbackDeadlineDays() <= 0) {
			throw new IllegalArgumentException("Fallback deadline days must be a positive integer");
		}
		if (form.getSensitiveDataRetentionDays() <= 0) {
			throw new IllegalArgumentException("Sensitive data retention days must be a positive integer");
		}
		if (form.getLockoutDurationMinutes() <= 0) {
			throw new IllegalArgumentException("Lockout duration minutes must be a positive integer");
		}
		if (form.getLockoutThreshold() <= 0) {
			throw new IllegalArgumentException("Lockout threshold must be a positive integer");
		}

		validateTimeOnGrid(form.getMorningStartTime(), "Morning start time");
		validateTimeOnGrid(form.getMorningEndTime(), "Morning end time");
		validateTimeOnGrid(form.getAfternoonStartTime(), "Afternoon start time");
		validateTimeOnGrid(form.getAfternoonEndTime(), "Afternoon end time");
		validateTimeOnGrid(form.getEveningStartTime(), "Evening start time");
		validateTimeOnGrid(form.getEveningEndTime(), "Evening end time");

		if (!form.getMorningStartTime().isBefore(form.getMorningEndTime())) {
			throw new IllegalArgumentException("Morning start time must be before morning end time");
		}
		if (form.getMorningEndTime().isAfter(form.getAfternoonStartTime())) {
			throw new IllegalArgumentException("Morning end time cannot be after afternoon start time");
		}
		if (!form.getAfternoonStartTime().isBefore(form.getAfternoonEndTime())) {
			throw new IllegalArgumentException("Afternoon start time must be before afternoon end time");
		}
		if (form.getAfternoonEndTime().isAfter(form.getEveningStartTime())) {
			throw new IllegalArgumentException("Afternoon end time cannot be after evening start time");
		}
		if (!form.getEveningStartTime().isBefore(form.getEveningEndTime())) {
			throw new IllegalArgumentException("Evening start time must be before evening end time");
		}

		if (form.getUrgentCareGuidance() == null || form.getUrgentCareGuidance().trim().isEmpty()) {
			throw new IllegalArgumentException("Urgent care guidance cannot be blank");
		}
		if (form.getUrgentCareGuidance().length() > 1000) {
			throw new IllegalArgumentException("Urgent care guidance cannot exceed 1000 characters");
		}
		if (form.getEmergencyPhone() == null || form.getEmergencyPhone().trim().isEmpty()) {
			throw new IllegalArgumentException("Emergency phone number cannot be blank");
		}
		if (form.getEmergencyPhone().length() > 50) {
			throw new IllegalArgumentException("Emergency phone number cannot exceed 50 characters");
		}
	}

	private void validateTimeOnGrid(LocalTime time, String fieldName) {
		if (time == null) {
			throw new IllegalArgumentException(fieldName + " cannot be null");
		}
		if (!timeService.isGridAligned(time)) {
			throw new IllegalArgumentException(fieldName + " must be aligned to the 15-minute grid (00, 15, 30, 45)");
		}
	}

}
