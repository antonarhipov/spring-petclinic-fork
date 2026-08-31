package org.springframework.samples.petclinic.scheduling.queue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;

public class QueueActionForms {

	public static class ContactAttemptForm {

		private ContactOutcome outcome = ContactOutcome.REACHED_AGREED;

		private String note;

		public ContactOutcome getOutcome() {
			return this.outcome;
		}

		public void setOutcome(ContactOutcome outcome) {
			this.outcome = outcome;
		}

		public String getNote() {
			return this.note;
		}

		public void setNote(String note) {
			this.note = note;
		}

	}

	public static class ReassignForm {

		private Long assigneeAccountId;

		public Long getAssigneeAccountId() {
			return this.assigneeAccountId;
		}

		public void setAssigneeAccountId(Long assigneeAccountId) {
			this.assigneeAccountId = assigneeAccountId;
		}

	}

	public static class ManualInterpretationForm {

		private String visitReason;

		private Integer durationMinutes = 30;

		private Integer preferredVetId;

		private Integer requiredSpecialtyId;

		private Urgency urgency = Urgency.ROUTINE;

		private LocalDate startDate;

		private LocalDate endDate;

		private LocalTime startTime;

		private LocalTime endTime;

		private boolean requestOwnerConfirmation = true;

		public String getVisitReason() {
			return this.visitReason;
		}

		public void setVisitReason(String visitReason) {
			this.visitReason = visitReason;
		}

		public Integer getDurationMinutes() {
			return this.durationMinutes;
		}

		public void setDurationMinutes(Integer durationMinutes) {
			this.durationMinutes = durationMinutes;
		}

		public Integer getPreferredVetId() {
			return this.preferredVetId;
		}

		public void setPreferredVetId(Integer preferredVetId) {
			this.preferredVetId = preferredVetId;
		}

		public Integer getRequiredSpecialtyId() {
			return this.requiredSpecialtyId;
		}

		public void setRequiredSpecialtyId(Integer requiredSpecialtyId) {
			this.requiredSpecialtyId = requiredSpecialtyId;
		}

		public Urgency getUrgency() {
			return this.urgency;
		}

		public void setUrgency(Urgency urgency) {
			this.urgency = urgency;
		}

		public LocalDate getStartDate() {
			return this.startDate;
		}

		public void setStartDate(LocalDate startDate) {
			this.startDate = startDate;
		}

		public LocalDate getEndDate() {
			return this.endDate;
		}

		public void setEndDate(LocalDate endDate) {
			this.endDate = endDate;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public LocalTime getEndTime() {
			return this.endTime;
		}

		public void setEndTime(LocalTime endTime) {
			this.endTime = endTime;
		}

		public boolean isRequestOwnerConfirmation() {
			return this.requestOwnerConfirmation;
		}

		public void setRequestOwnerConfirmation(boolean requestOwnerConfirmation) {
			this.requestOwnerConfirmation = requestOwnerConfirmation;
		}

	}

	public static class EmergencyClearanceForm {

		private Urgency newUrgency = Urgency.ROUTINE;

		private String clinicalJustification;

		public Urgency getNewUrgency() {
			return this.newUrgency;
		}

		public void setNewUrgency(Urgency newUrgency) {
			this.newUrgency = newUrgency;
		}

		public String getClinicalJustification() {
			return this.clinicalJustification;
		}

		public void setClinicalJustification(String clinicalJustification) {
			this.clinicalJustification = clinicalJustification;
		}

	}

	public static class AssistedOfferForm {

		private Integer vetId;

		private LocalDate date;

		private LocalTime startTime;

		private Integer durationMinutes = 30;

		private String explanation;

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
		}

		public LocalDate getDate() {
			return this.date;
		}

		public void setDate(LocalDate date) {
			this.date = date;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public Integer getDurationMinutes() {
			return this.durationMinutes;
		}

		public void setDurationMinutes(Integer durationMinutes) {
			this.durationMinutes = durationMinutes;
		}

		public String getExplanation() {
			return this.explanation;
		}

		public void setExplanation(String explanation) {
			this.explanation = explanation;
		}

	}

	public static class QueueDirectBookForm {

		private Integer vetId;

		private LocalDate date;

		private LocalTime startTime;

		private Integer durationMinutes = 30;

		private boolean ownerAgreementRecorded = true;

		private String agreementMedium = "PHONE";

		private String internalReason;

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
		}

		public LocalDate getDate() {
			return this.date;
		}

		public void setDate(LocalDate date) {
			this.date = date;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public Integer getDurationMinutes() {
			return this.durationMinutes;
		}

		public void setDurationMinutes(Integer durationMinutes) {
			this.durationMinutes = durationMinutes;
		}

		public boolean isOwnerAgreementRecorded() {
			return this.ownerAgreementRecorded;
		}

		public void setOwnerAgreementRecorded(boolean ownerAgreementRecorded) {
			this.ownerAgreementRecorded = ownerAgreementRecorded;
		}

		public String getAgreementMedium() {
			return this.agreementMedium;
		}

		public void setAgreementMedium(String agreementMedium) {
			this.agreementMedium = agreementMedium;
		}

		public String getInternalReason() {
			return this.internalReason;
		}

		public void setInternalReason(String internalReason) {
			this.internalReason = internalReason;
		}

	}

	public static class CloseQueueForm {

		private String reason;

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

	}

}
