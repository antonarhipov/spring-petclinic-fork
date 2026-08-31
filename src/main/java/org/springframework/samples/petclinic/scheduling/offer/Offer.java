package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Instant;
import java.util.Objects;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;

@Entity
@Table(name = "offers")
public class Offer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_revision_id", nullable = false)
	private WorkflowRevision workflowRevision;

	@Column(name = "owner_id", nullable = false)
	private Integer ownerId;

	@Column(name = "pet_id", nullable = false)
	private Integer petId;

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin", nullable = false, length = 32)
	private OfferOrigin origin;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "zone_id", nullable = false, length = 64)
	private String zoneId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 32)
	private OfferState state;

	@Column(name = "automatic_attempt_number")
	private Integer automaticAttemptNumber;

	@Column(name = "calendar_revision", nullable = false)
	private Long calendarRevision;

	@Column(name = "match_explanation", nullable = false, columnDefinition = "TEXT")
	private String matchExplanation;

	@Column(name = "appointment_id", unique = true)
	private Long appointmentId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	public Offer() {
	}

	public Offer(SchedulingRequest request, WorkflowRevision workflowRevision, Integer vetId, Instant startAt,
			Instant endAt, Instant expiresAt, OfferOrigin origin, Integer automaticAttemptNumber) {
		this(request, workflowRevision, request != null ? request.getOwnerId() : 1,
				request != null ? request.getPetId() : 1, vetId, origin, startAt, endAt, "America/New_York", expiresAt,
				OfferState.HELD, automaticAttemptNumber, 1L, "Deterministic slot match");
	}

	public Offer(SchedulingRequest request, WorkflowRevision workflowRevision, Integer ownerId, Integer petId,
			Integer vetId, OfferOrigin origin, Instant startAt, Instant endAt, String zoneId, Instant expiresAt,
			OfferState state, Integer automaticAttemptNumber, Long calendarRevision, String matchExplanation) {
		this.request = Objects.requireNonNull(request, "request must not be null");
		this.workflowRevision = Objects.requireNonNull(workflowRevision, "workflowRevision must not be null");
		this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
		this.petId = Objects.requireNonNull(petId, "petId must not be null");
		this.vetId = Objects.requireNonNull(vetId, "vetId must not be null");
		this.origin = Objects.requireNonNull(origin, "origin must not be null");
		this.startAt = Objects.requireNonNull(startAt, "startAt must not be null");
		this.endAt = Objects.requireNonNull(endAt, "endAt must not be null");
		this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.automaticAttemptNumber = automaticAttemptNumber;
		this.calendarRevision = Objects.requireNonNull(calendarRevision, "calendarRevision must not be null");
		this.matchExplanation = Objects.requireNonNull(matchExplanation, "matchExplanation must not be null");
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public WorkflowRevision getWorkflowRevision() {
		return this.workflowRevision;
	}

	public void setWorkflowRevision(WorkflowRevision workflowRevision) {
		this.workflowRevision = workflowRevision;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public OfferOrigin getOrigin() {
		return this.origin;
	}

	public void setOrigin(OfferOrigin origin) {
		this.origin = origin;
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

	public String getZoneId() {
		return this.zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public OfferState getState() {
		return this.state;
	}

	public void setState(OfferState state) {
		this.state = state;
	}

	public Integer getAutomaticAttemptNumber() {
		return this.automaticAttemptNumber;
	}

	public void setAutomaticAttemptNumber(Integer automaticAttemptNumber) {
		this.automaticAttemptNumber = automaticAttemptNumber;
	}

	public Long getCalendarRevision() {
		return this.calendarRevision;
	}

	public void setCalendarRevision(Long calendarRevision) {
		this.calendarRevision = calendarRevision;
	}

	public String getMatchExplanation() {
		return this.matchExplanation;
	}

	public void setMatchExplanation(String matchExplanation) {
		this.matchExplanation = matchExplanation;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public void setAppointmentId(Long appointmentId) {
		this.appointmentId = appointmentId;
	}

	public Long getVersion() {
		return this.version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
