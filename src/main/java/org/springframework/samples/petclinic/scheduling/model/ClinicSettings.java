package org.springframework.samples.petclinic.scheduling.model;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "clinic_settings")
public class ClinicSettings {

	@Id
	private Integer id = 1;

	@Column(name = "min_duration_minutes", nullable = false)
	private int minDurationMinutes = 15;

	@Column(name = "max_duration_minutes", nullable = false)
	private int maxDurationMinutes = 120;

	@Column(name = "default_duration_minutes", nullable = false)
	private int defaultDurationMinutes = 30;

	@Column(name = "owner_booking_horizon_days", nullable = false)
	private int ownerBookingHorizonDays = 56;

	@Column(name = "staff_booking_horizon_days", nullable = false)
	private int staffBookingHorizonDays = 365;

	@Column(name = "guided_hold_duration_minutes", nullable = false)
	private int guidedHoldDurationMinutes = 5;

	@Column(name = "staff_offer_hold_duration_hours", nullable = false)
	private int staffOfferHoldDurationHours = 24;

	@Column(name = "staff_claim_inactivity_minutes", nullable = false)
	private int staffClaimInactivityMinutes = 30;

	@Column(name = "fallback_deadline_days", nullable = false)
	private int fallbackDeadlineDays = 7;

	@Column(name = "sensitive_data_retention_days", nullable = false)
	private int sensitiveDataRetentionDays = 30;

	@Column(name = "lockout_duration_minutes", nullable = false)
	private int lockoutDurationMinutes = 15;

	@Column(name = "lockout_threshold", nullable = false)
	private int lockoutThreshold = 5;

	@Column(name = "zone_id", nullable = false, length = 50)
	private String zoneId = "UTC";

	@Column(name = "morning_start_time", nullable = false)
	private LocalTime morningStartTime = LocalTime.of(8, 0);

	@Column(name = "morning_end_time", nullable = false)
	private LocalTime morningEndTime = LocalTime.of(12, 0);

	@Column(name = "afternoon_start_time", nullable = false)
	private LocalTime afternoonStartTime = LocalTime.of(12, 0);

	@Column(name = "afternoon_end_time", nullable = false)
	private LocalTime afternoonEndTime = LocalTime.of(17, 0);

	@Column(name = "evening_start_time", nullable = false)
	private LocalTime eveningStartTime = LocalTime.of(17, 0);

	@Column(name = "evening_end_time", nullable = false)
	private LocalTime eveningEndTime = LocalTime.of(20, 0);

	@Column(name = "urgent_care_guidance", nullable = false, length = 1000)
	private String urgentCareGuidance = "If your pet requires immediate medical attention, please visit the emergency clinic or call our urgent care line.";

	@Column(name = "emergency_phone", nullable = false, length = 50)
	private String emergencyPhone = "608-555-0199";

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	public ClinicSettings() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

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

	public String getZoneId() {
		return zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
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
