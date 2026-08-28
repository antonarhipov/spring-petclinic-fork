package org.springframework.samples.petclinic.scheduling.availability;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "clinic_scheduling_settings")
public class ClinicSchedulingSettings extends BaseEntity {

	@Column(name = "clinic_zone", nullable = false)
	private String clinicZone;

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays;

	@Column(name = "owner_minimum_notice_minutes", nullable = false)
	private int ownerMinimumNoticeMinutes;

	@Column(name = "offer_hold_minutes", nullable = false)
	private int offerHoldMinutes;

	@Column(name = "urgent_care_guidance", nullable = false)
	private String urgentCareGuidance;

	@Version
	private long version;

	protected ClinicSchedulingSettings() {
	}

	public String getClinicZone() {
		return this.clinicZone;
	}

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public int getOwnerMinimumNoticeMinutes() {
		return this.ownerMinimumNoticeMinutes;
	}

	public int getOfferHoldMinutes() {
		return this.offerHoldMinutes;
	}

	public String getUrgentCareGuidance() {
		return this.urgentCareGuidance;
	}

	public void update(String clinicZone, int horizon, int notice, int hold, String guidance) {
		this.clinicZone = clinicZone;
		this.bookingHorizonDays = horizon;
		this.ownerMinimumNoticeMinutes = notice;
		this.offerHoldMinutes = hold;
		this.urgentCareGuidance = guidance;
	}

}
