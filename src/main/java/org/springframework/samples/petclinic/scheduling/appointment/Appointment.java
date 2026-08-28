package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentSource source;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id")
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id")
	private RequestRevision revision;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "offer_id")
	private AppointmentOffer offer;

	@Column(name = "owner_agreement")
	private String ownerAgreement;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentStatus status = AppointmentStatus.CONFIRMED;

	@Column(name = "change_reason")
	private String changeReason;

	@Column(name = "change_note")
	private String changeNote;

	@Version
	private long version;

	protected Appointment() {
	}

	public Appointment(Pet pet, Vet vet, Instant startAt, int durationMinutes, AppointmentSource source,
			SchedulingRequest request, RequestRevision revision, AppointmentOffer offer, String ownerAgreement) {
		this.pet = pet;
		this.vet = vet;
		this.startAt = startAt;
		this.durationMinutes = durationMinutes;
		this.source = source;
		this.request = request;
		this.revision = revision;
		this.offer = offer;
		this.ownerAgreement = ownerAgreement;
	}

	public Pet getPet() {
		return this.pet;
	}

	public Vet getVet() {
		return this.vet;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public AppointmentSource getSource() {
		return this.source;
	}

	public String getOwnerAgreement() {
		return this.ownerAgreement;
	}

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public RequestRevision getRevision() {
		return this.revision;
	}

	public String getChangeReason() {
		return this.changeReason;
	}

	public String getChangeNote() {
		return this.changeNote;
	}

	public void reschedule(Vet vet, Instant startAt, String reason, String note) {
		this.vet = vet;
		this.startAt = startAt;
		this.changeReason = reason;
		this.changeNote = note;
	}

	public void changeStatus(AppointmentStatus status, String reason, String note) {
		this.status = status;
		this.changeReason = reason;
		this.changeNote = note;
	}

}
