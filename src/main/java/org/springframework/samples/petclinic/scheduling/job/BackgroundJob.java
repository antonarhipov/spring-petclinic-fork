package org.springframework.samples.petclinic.scheduling.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;

@Entity
@Table(name = "background_jobs")
public class BackgroundJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 32)
	private JobType jobType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "text_revision_id")
	private TextRevision textRevision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_revision_id")
	private WorkflowRevision workflowRevision;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 32)
	private JobState state;

	@Column(name = "run_sequence", nullable = false)
	private Integer runSequence = 1;

	@Column(name = "attempt_count", nullable = false)
	private Integer attemptCount = 0;

	@Column(name = "calendar_retry_count", nullable = false)
	private Integer calendarRetryCount = 0;

	@Column(name = "lease_token", length = 64)
	private String leaseToken;

	@Column(name = "lease_until")
	private Instant leaseUntil;

	@Column(name = "available_at")
	private Instant availableAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "calendar_revision")
	private Long calendarRevision;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome_category", length = 64)
	private OutcomeCategory outcomeCategory;

	@Column(name = "command_id", nullable = false, unique = true, updatable = false)
	private UUID commandId = UUID.randomUUID();

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	public BackgroundJob() {
	}

	public BackgroundJob(JobType jobType, TextRevision textRevision, WorkflowRevision workflowRevision, JobState state,
			Instant availableAt) {
		this.jobType = Objects.requireNonNull(jobType, "jobType must not be null");
		this.textRevision = textRevision;
		this.workflowRevision = workflowRevision;
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.availableAt = (availableAt != null) ? availableAt : Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public JobType getJobType() {
		return this.jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public TextRevision getTextRevision() {
		return this.textRevision;
	}

	public void setTextRevision(TextRevision textRevision) {
		this.textRevision = textRevision;
	}

	public WorkflowRevision getWorkflowRevision() {
		return this.workflowRevision;
	}

	public void setWorkflowRevision(WorkflowRevision workflowRevision) {
		this.workflowRevision = workflowRevision;
	}

	public JobState getState() {
		return this.state;
	}

	public void setState(JobState state) {
		this.state = state;
	}

	public Integer getRunSequence() {
		return this.runSequence;
	}

	public void setRunSequence(Integer runSequence) {
		this.runSequence = runSequence;
	}

	public Integer getAttemptCount() {
		return this.attemptCount;
	}

	public void setAttemptCount(Integer attemptCount) {
		this.attemptCount = attemptCount;
	}

	public Integer getCalendarRetryCount() {
		return this.calendarRetryCount;
	}

	public void setCalendarRetryCount(Integer calendarRetryCount) {
		this.calendarRetryCount = calendarRetryCount;
	}

	public String getLeaseToken() {
		return this.leaseToken;
	}

	public void setLeaseToken(String leaseToken) {
		this.leaseToken = leaseToken;
	}

	public Instant getLeaseUntil() {
		return this.leaseUntil;
	}

	public void setLeaseUntil(Instant leaseUntil) {
		this.leaseUntil = leaseUntil;
	}

	public Instant getAvailableAt() {
		return this.availableAt;
	}

	public void setAvailableAt(Instant availableAt) {
		this.availableAt = availableAt;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Long getCalendarRevision() {
		return this.calendarRevision;
	}

	public void setCalendarRevision(Long calendarRevision) {
		this.calendarRevision = calendarRevision;
	}

	public OutcomeCategory getOutcomeCategory() {
		return this.outcomeCategory;
	}

	public UUID getCommandId() {
		return this.commandId;
	}

	public void setCommandId(UUID commandId) {
		this.commandId = commandId;
	}

	public void setOutcomeCategory(OutcomeCategory outcomeCategory) {
		this.outcomeCategory = outcomeCategory;
	}

	public Long getVersion() {
		return this.version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
