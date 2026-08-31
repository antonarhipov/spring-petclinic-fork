package org.springframework.samples.petclinic.scheduling.queue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;

public class QueueActionForms {

	public abstract static class VersionedQueueForm {

		private Long expectedRequestVersion;

		private Integer expectedWorkflowRevision;

		private Long expectedQueueVersion;

		public Long getExpectedRequestVersion() {
			return this.expectedRequestVersion;
		}

		public void setExpectedRequestVersion(Long expectedRequestVersion) {
			this.expectedRequestVersion = expectedRequestVersion;
		}

		public Integer getExpectedWorkflowRevision() {
			return this.expectedWorkflowRevision;
		}

		public void setExpectedWorkflowRevision(Integer expectedWorkflowRevision) {
			this.expectedWorkflowRevision = expectedWorkflowRevision;
		}

		public Long getExpectedQueueVersion() {
			return this.expectedQueueVersion;
		}

		public void setExpectedQueueVersion(Long expectedQueueVersion) {
			this.expectedQueueVersion = expectedQueueVersion;
		}

	}

	public static class ContactAttemptForm extends VersionedQueueForm {

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

	public static class ClaimForm extends VersionedQueueForm {

	}

	public static class ReassignForm extends VersionedQueueForm {

		private Long assigneeAccountId;

		private String reason;

		public Long getAssigneeAccountId() {
			return this.assigneeAccountId;
		}

		public void setAssigneeAccountId(Long assigneeAccountId) {
			this.assigneeAccountId = assigneeAccountId;
		}

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

	}

	public static class UnclaimForm extends VersionedQueueForm {

		private String reason;

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

	}

	public static class ManualInterpretationForm extends VersionedQueueForm {

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

	public static class EmergencyClearanceForm extends VersionedQueueForm {

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

	public static class AssistedOfferForm extends VersionedQueueForm {

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

	public static class QueueDirectBookForm extends VersionedQueueForm {

		private Integer vetId;

		private LocalDate date;

		private LocalTime startTime;

		private Integer durationMinutes = 30;

		private boolean ownerAgreementRecorded = true;

		private String agreementMedium = "PHONE";

		private String reasonCategory = "OWNER_REQUEST";

		private String internalReason;

		private boolean confirmed;

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

		public String getReasonCategory() {
			return this.reasonCategory;
		}

		public void setReasonCategory(String reasonCategory) {
			this.reasonCategory = reasonCategory;
		}

		public void setInternalReason(String internalReason) {
			this.internalReason = internalReason;
		}

		public boolean isConfirmed() {
			return this.confirmed;
		}

		public void setConfirmed(boolean confirmed) {
			this.confirmed = confirmed;
		}

	}

	public static class CloseQueueForm extends VersionedQueueForm {

		private String reason;

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

	}

}
