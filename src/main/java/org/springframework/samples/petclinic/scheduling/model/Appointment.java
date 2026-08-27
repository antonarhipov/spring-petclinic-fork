package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private AppointmentStatus status = AppointmentStatus.BOOKED;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id")
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Enumerated(EnumType.STRING)
	@Column(name = "care_type", nullable = false, length = 30)
	private CareType careType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "required_specialty_id")
	private Specialty requiredSpecialty;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "booking_source", nullable = false, length = 30)
	private BookingSource bookingSource;

	@Column(name = "offline_reason", length = 255)
	private String offlineReason;

	@Column(name = "cancellation_reason", length = 255)
	private String cancellationReason;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "completion_notes", length = 1000)
	private String completionNotes;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "visit_id")
	private Visit visit;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Appointment() {
	}

	@PrePersist
	public void onPrePersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		if (this.updatedAt == null) {
			this.updatedAt = now;
		}
	}

	@PreUpdate
	public void onPreUpdate() {
		this.updatedAt = Instant.now();
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public void setStatus(AppointmentStatus status) {
		this.status = status;
	}

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public Owner getOwner() {
		return owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public Pet getPet() {
		return pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

	public Vet getVet() {
		return vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public CareType getCareType() {
		return careType;
	}

	public void setCareType(CareType careType) {
		this.careType = careType;
	}

	public Specialty getRequiredSpecialty() {
		return requiredSpecialty;
	}

	public void setRequiredSpecialty(Specialty requiredSpecialty) {
		this.requiredSpecialty = requiredSpecialty;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}

	public BookingSource getBookingSource() {
		return bookingSource;
	}

	public void setBookingSource(BookingSource bookingSource) {
		this.bookingSource = bookingSource;
	}

	public String getOfflineReason() {
		return offlineReason;
	}

	public void setOfflineReason(String offlineReason) {
		this.offlineReason = offlineReason;
	}

	public String getCancellationReason() {
		return cancellationReason;
	}

	public void setCancellationReason(String cancellationReason) {
		this.cancellationReason = cancellationReason;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public String getCompletionNotes() {
		return completionNotes;
	}

	public void setCompletionNotes(String completionNotes) {
		this.completionNotes = completionNotes;
	}

	public Visit getVisit() {
		return visit;
	}

	public void setVisit(Visit visit) {
		this.visit = visit;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "Appointment{" + "id=" + getId() + ", status=" + status + ", ownerId="
				+ (owner != null ? owner.getId() : null) + ", petId=" + (pet != null ? pet.getId() : null) + ", vetId="
				+ (vet != null ? vet.getId() : null) + ", careType=" + careType + ", startTime=" + startTime
				+ ", endTime=" + endTime + ", bookingSource=" + bookingSource + ", version=" + version + '}';
	}

}
