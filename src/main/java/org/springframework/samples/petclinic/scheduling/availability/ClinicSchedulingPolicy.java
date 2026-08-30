package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "clinic_scheduling_policies")
public class ClinicSchedulingPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "zone_id", nullable = false)
	private String zoneId;

	@Column(name = "grid_minutes", nullable = false)
	private int gridMinutes = 15;

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays;

	@Column(name = "hold_duration_minutes", nullable = false)
	private int holdDurationMinutes;

	@Column(name = "owner_minimum_notice_minutes", nullable = false)
	private int ownerMinimumNoticeMinutes = 120;

	@Column(name = "urgent_care_guidance", nullable = false)
	private String urgentCareGuidance;

	@Column(name = "consent_copy_version", nullable = false)
	private String consentCopyVersion;

	@Column(name = "configuration_version", nullable = false)
	private long configurationVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

	public int getGridMinutes() {
		return this.gridMinutes;
	}

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(int bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public int getHoldDurationMinutes() {
		return this.holdDurationMinutes;
	}

	public void setHoldDurationMinutes(int holdDurationMinutes) {
		this.holdDurationMinutes = holdDurationMinutes;
	}

	public int getOwnerMinimumNoticeMinutes() {
		return this.ownerMinimumNoticeMinutes;
	}

	public long getConfigurationVersion() {
		return this.configurationVersion;
	}

	public void setConfigurationVersion(long configurationVersion) {
		this.configurationVersion = configurationVersion;
	}

	public Integer getVersion() {
		return this.version;
	}

	public String getUrgentCareGuidance() {
		return this.urgentCareGuidance;
	}

	public String getConsentCopyVersion() {
		return this.consentCopyVersion;
	}

	public void setConsentCopyVersion(String consentCopyVersion) {
		this.consentCopyVersion = consentCopyVersion;
	}

	public void setUrgentCareGuidance(String urgentCareGuidance) {
		this.urgentCareGuidance = urgentCareGuidance;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
