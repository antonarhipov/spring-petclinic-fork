package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.Instant;

public class StaffBookingForm {

	private int veterinarianId;

	private Instant startAt;

	private Instant endAt;

	private String authorizationBasis;

	private Instant agreementAt;

	private String agreementMethod;

	private Integer supportingVisitId;

	private String staffReason;

	public int getVeterinarianId() {
		return this.veterinarianId;
	}

	public void setVeterinarianId(int veterinarianId) {
		this.veterinarianId = veterinarianId;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public void setStartAt(Instant startAt) {
		this.startAt = startAt;
	}

	public Instant getEndAt() {
		return this.endAt;
	}

	public void setEndAt(Instant endAt) {
		this.endAt = endAt;
	}

	public String getAuthorizationBasis() {
		return this.authorizationBasis;
	}

	public void setAuthorizationBasis(String authorizationBasis) {
		this.authorizationBasis = authorizationBasis;
	}

	public Instant getAgreementAt() {
		return this.agreementAt;
	}

	public void setAgreementAt(Instant agreementAt) {
		this.agreementAt = agreementAt;
	}

	public String getAgreementMethod() {
		return this.agreementMethod;
	}

	public void setAgreementMethod(String agreementMethod) {
		this.agreementMethod = agreementMethod;
	}

	public Integer getSupportingVisitId() {
		return this.supportingVisitId;
	}

	public void setSupportingVisitId(Integer supportingVisitId) {
		this.supportingVisitId = supportingVisitId;
	}

	public String getStaffReason() {
		return this.staffReason;
	}

	public void setStaffReason(String staffReason) {
		this.staffReason = staffReason;
	}

}
