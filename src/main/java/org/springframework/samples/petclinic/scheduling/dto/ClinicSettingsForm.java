package org.springframework.samples.petclinic.scheduling.dto;

import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

public class ClinicSettingsForm {

	private int minDurationMinutes = 15;

	private int maxDurationMinutes = 120;

	private int defaultDurationMinutes = 30;

	private int ownerBookingHorizonDays = 56;

	private int staffBookingHorizonDays = 365;

	private int guidedHoldDurationMinutes = 5;

	private int staffOfferHoldDurationHours = 24;

	private int staffClaimInactivityMinutes = 30;

	private int fallbackDeadlineDays = 7;

	private int sensitiveDataRetentionDays = 30;

	private int lockoutDurationMinutes = 15;

	private int lockoutThreshold = 5;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime morningStartTime = LocalTime.of(8, 0);

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime morningEndTime = LocalTime.of(12, 0);

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime afternoonStartTime = LocalTime.of(12, 0);

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime afternoonEndTime = LocalTime.of(17, 0);

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime eveningStartTime = LocalTime.of(17, 0);

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime eveningEndTime = LocalTime.of(20, 0);

	private String urgentCareGuidance = "If your pet requires immediate medical attention, please visit the emergency clinic or call our urgent care line.";

	private String emergencyPhone = "608-555-0199";

	private Long version = 0L;

	public int getMinDurationMinutes() {
		return minDurationMinutes;
	}

	public void setMinDurationMinutes(int minDurationMinutes) {
		this.minDurationMinutes = minDurationMinutes;
	}

	public int getMaxDurationMinutes() {
		return maxDurationMinutes;
	}

	public void setMaxDurationMinutes(int maxDurationMinutes) {
		this.maxDurationMinutes = maxDurationMinutes;
	}

	public int getDefaultDurationMinutes() {
		return defaultDurationMinutes;
	}

	public void setDefaultDurationMinutes(int defaultDurationMinutes) {
		this.defaultDurationMinutes = defaultDurationMinutes;
	}

	public int getOwnerBookingHorizonDays() {
		return ownerBookingHorizonDays;
	}

	public void setOwnerBookingHorizonDays(int ownerBookingHorizonDays) {
		this.ownerBookingHorizonDays = ownerBookingHorizonDays;
	}

	public int getStaffBookingHorizonDays() {
		return staffBookingHorizonDays;
	}

	public void setStaffBookingHorizonDays(int staffBookingHorizonDays) {
		this.staffBookingHorizonDays = staffBookingHorizonDays;
	}

	public int getGuidedHoldDurationMinutes() {
		return guidedHoldDurationMinutes;
	}

	public void setGuidedHoldDurationMinutes(int guidedHoldDurationMinutes) {
		this.guidedHoldDurationMinutes = guidedHoldDurationMinutes;
	}

	public int getStaffOfferHoldDurationHours() {
		return staffOfferHoldDurationHours;
	}

	public void setStaffOfferHoldDurationHours(int staffOfferHoldDurationHours) {
		this.staffOfferHoldDurationHours = staffOfferHoldDurationHours;
	}

	public int getStaffClaimInactivityMinutes() {
		return staffClaimInactivityMinutes;
	}

	public void setStaffClaimInactivityMinutes(int staffClaimInactivityMinutes) {
		this.staffClaimInactivityMinutes = staffClaimInactivityMinutes;
	}

	public int getFallbackDeadlineDays() {
		return fallbackDeadlineDays;
	}

	public void setFallbackDeadlineDays(int fallbackDeadlineDays) {
		this.fallbackDeadlineDays = fallbackDeadlineDays;
	}

	public int getSensitiveDataRetentionDays() {
		return sensitiveDataRetentionDays;
	}

	public void setSensitiveDataRetentionDays(int sensitiveDataRetentionDays) {
		this.sensitiveDataRetentionDays = sensitiveDataRetentionDays;
	}

	public int getLockoutDurationMinutes() {
		return lockoutDurationMinutes;
	}

	public void setLockoutDurationMinutes(int lockoutDurationMinutes) {
		this.lockoutDurationMinutes = lockoutDurationMinutes;
	}

	public int getLockoutThreshold() {
		return lockoutThreshold;
	}

	public void setLockoutThreshold(int lockoutThreshold) {
		this.lockoutThreshold = lockoutThreshold;
	}

	public LocalTime getMorningStartTime() {
		return morningStartTime;
	}

	public void setMorningStartTime(LocalTime morningStartTime) {
		this.morningStartTime = morningStartTime;
	}

	public LocalTime getMorningEndTime() {
		return morningEndTime;
	}

	public void setMorningEndTime(LocalTime morningEndTime) {
		this.morningEndTime = morningEndTime;
	}

	public LocalTime getAfternoonStartTime() {
		return afternoonStartTime;
	}

	public void setAfternoonStartTime(LocalTime afternoonStartTime) {
		this.afternoonStartTime = afternoonStartTime;
	}

	public LocalTime getAfternoonEndTime() {
		return afternoonEndTime;
	}

	public void setAfternoonEndTime(LocalTime afternoonEndTime) {
		this.afternoonEndTime = afternoonEndTime;
	}

	public LocalTime getEveningStartTime() {
		return eveningStartTime;
	}

	public void setEveningStartTime(LocalTime eveningStartTime) {
		this.eveningStartTime = eveningStartTime;
	}

	public LocalTime getEveningEndTime() {
		return eveningEndTime;
	}

	public void setEveningEndTime(LocalTime eveningEndTime) {
		this.eveningEndTime = eveningEndTime;
	}

	public String getUrgentCareGuidance() {
		return urgentCareGuidance;
	}

	public void setUrgentCareGuidance(String urgentCareGuidance) {
		this.urgentCareGuidance = urgentCareGuidance;
	}

	public String getEmergencyPhone() {
		return emergencyPhone;
	}

	public void setEmergencyPhone(String emergencyPhone) {
		this.emergencyPhone = emergencyPhone;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
