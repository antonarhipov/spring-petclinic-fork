package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Column(name = "active_pet_id", unique = true)
	private Integer activePetId;

	@NotBlank
	@Size(min = 1, max = 2000)
	@Column(name = "request_text", nullable = false, length = 2000)
	private String requestText;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 32)
	private RequestState state;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@Column(name = "created_time", nullable = false)
	private LocalTime createdTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "with_staff_reason", length = 32)
	private WithStaffReason withStaffReason;

	@Column(name = "staff_reason", length = 255)
	private String staffReason;

	@Column(name = "failure_count", nullable = false)
	private int failureCount;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_interpretation_id")
	private Interpretation currentInterpretation;

	@OneToMany(mappedBy = "request")
	private List<Interpretation> interpretations = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
	private List<InterpretationFailure> failures = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Rejection> rejections = new ArrayList<>();

	@Column(name = "updated_date")
	private LocalDate updatedDate;

	@Column(name = "updated_time")
	private LocalTime updatedTime;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected SchedulingRequest() {
	}

	private SchedulingRequest(Pet pet, String requestText, RequestState state, LocalDate createdDate,
			LocalTime createdTime) {
		this.pet = pet;
		this.activePetId = pet.getId();
		this.requestText = requestText;
		this.state = state;
		this.withStaffReason = state == RequestState.WITH_STAFF ? WithStaffReason.STAFF_CREATED : null;
		this.createdDate = createdDate;
		this.createdTime = createdTime;
	}

	public static SchedulingRequest awaitingConsent(Pet pet, String requestText, LocalDate createdDate,
			LocalTime createdTime) {
		return new SchedulingRequest(pet, requestText, RequestState.AWAITING_CONSENT, createdDate, createdTime);
	}

	public static SchedulingRequest withStaff(Pet pet, String requestText, LocalDate createdDate,
			LocalTime createdTime) {
		return new SchedulingRequest(pet, requestText, RequestState.WITH_STAFF, createdDate, createdTime);
	}

	public Pet getPet() {
		return this.pet;
	}

	public Integer getActivePetId() {
		return this.activePetId;
	}

	public String getRequestText() {
		return this.requestText;
	}

	public RequestState getState() {
		return this.state;
	}

	public LocalDate getCreatedDate() {
		return this.createdDate;
	}

	public LocalTime getCreatedTime() {
		return this.createdTime;
	}

	public long getVersion() {
		return this.version;
	}

	public WithStaffReason getWithStaffReason() {
		return this.withStaffReason;
	}

	public String getStaffReason() {
		return this.staffReason;
	}

	public int getFailureCount() {
		return this.failureCount;
	}

	public Interpretation getCurrentInterpretation() {
		return this.currentInterpretation;
	}

	public List<Interpretation> getInterpretations() {
		return Collections.unmodifiableList(this.interpretations);
	}

	public List<InterpretationFailure> getFailures() {
		return Collections.unmodifiableList(this.failures);
	}

	public List<Rejection> getRejections() {
		return Collections.unmodifiableList(this.rejections);
	}

	public void addRejection(Vet vet, LocalDate date, LocalTime startTime, LocalDate today, LocalTime now) {
		Rejection rejection = new Rejection(vet, date, startTime);
		rejection.attachTo(this);
		this.rejections.add(rejection);
		this.updatedDate = today;
		this.updatedTime = now;
	}

	public LocalDate getUpdatedDate() {
		return this.updatedDate;
	}

	public LocalTime getUpdatedTime() {
		return this.updatedTime;
	}

	void transitionTo(RequestState target, LocalDate date, LocalTime time) {
		this.state = target;
		this.updatedDate = date;
		this.updatedTime = time;
	}

	void routeToStaff(WithStaffReason reason, String staffReason, LocalDate date, LocalTime time) {
		this.withStaffReason = reason;
		this.staffReason = staffReason;
		transitionTo(RequestState.WITH_STAFF, date, time);
	}

	void replaceText(String requestText, LocalDate date, LocalTime time) {
		this.requestText = requestText;
		this.currentInterpretation = null;
		this.withStaffReason = null;
		this.staffReason = null;
		transitionTo(RequestState.AWAITING_CONSENT, date, time);
	}

	void addInterpretation(Interpretation interpretation, LocalDate date, LocalTime time) {
		this.interpretations.add(interpretation);
		this.currentInterpretation = interpretation;
		this.updatedDate = date;
		this.updatedTime = time;
	}

	void addFailure(InterpretationFailure failure, LocalDate date, LocalTime time) {
		this.failures.add(failure);
		this.failureCount++;
		transitionTo(RequestState.INTERPRETATION_FAILED, date, time);
	}

	void close(RequestState terminalState, LocalDate date, LocalTime time) {
		this.activePetId = null;
		transitionTo(terminalState, date, time);
	}

}
