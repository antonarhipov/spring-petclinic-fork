package org.springframework.samples.petclinic.scheduling.web.staff;

public class ClinicPolicyForm {

	private Integer expectedVersion;

	private Integer bookingHorizonDays;

	private Integer holdDurationMinutes;

	private String urgentCareGuidance;

	private String zoneId;

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

	public Integer getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(Integer bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public Integer getHoldDurationMinutes() {
		return this.holdDurationMinutes;
	}

	public void setHoldDurationMinutes(Integer holdDurationMinutes) {
		this.holdDurationMinutes = holdDurationMinutes;
	}

	public String getUrgentCareGuidance() {
		return this.urgentCareGuidance;
	}

	public void setUrgentCareGuidance(String urgentCareGuidance) {
		this.urgentCareGuidance = urgentCareGuidance;
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

}
