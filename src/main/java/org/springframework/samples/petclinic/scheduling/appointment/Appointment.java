package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;
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

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", unique = true)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "appointment_date", nullable = false)
	private LocalDate date;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private AppointmentStatus status;

	@Column(name = "held_date")
	private LocalDate heldDate;

	@Column(name = "held_time")
	private LocalTime heldTime;

	@Column(name = "rank_reason", length = 80)
	private String rankReason;

	@Column(name = "last_change_reason")
	private String lastChangeReason;

	@Column(name = "last_changed_by", length = 80)
	private String lastChangedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by", length = 16)
	private CancelledBy cancelledBy;

	@Column(name = "cancelled_date")
	private LocalDate cancelledDate;

	@Column(name = "cancelled_time")
	private LocalTime cancelledTime;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@Column(name = "created_time", nullable = false)
	private LocalTime createdTime;

	protected Appointment() {
	}

	private Appointment(Pet pet, Vet vet, LocalDate date, LocalTime startTime, LocalTime endTime,
			String lastChangeReason, String lastChangedBy, LocalDate createdDate, LocalTime createdTime) {
		this.pet = pet;
		this.vet = vet;
		this.date = date;
		this.startTime = startTime;
		this.endTime = endTime;
		this.status = AppointmentStatus.CONFIRMED;
		this.lastChangeReason = lastChangeReason;
		this.lastChangedBy = lastChangedBy;
		this.createdDate = createdDate;
		this.createdTime = createdTime;
	}

	public static Appointment confirmed(Pet pet, Vet vet, LocalDate date, LocalTime startTime, LocalTime endTime,
			String lastChangeReason, String lastChangedBy, LocalDate createdDate, LocalTime createdTime) {
		return new Appointment(pet, vet, date, startTime, endTime, lastChangeReason, lastChangedBy, createdDate,
				createdTime);
	}

	public static Appointment held(SchedulingRequest request, Vet vet, LocalDate date, LocalTime startTime,
			LocalTime endTime, String rankReason, LocalDate createdDate, LocalTime createdTime) {
		Appointment appointment = new Appointment(request.getPet(), vet, date, startTime, endTime, null, null,
				createdDate, createdTime);
		appointment.request = request;
		appointment.status = AppointmentStatus.HELD;
		appointment.heldDate = createdDate;
		appointment.heldTime = createdTime;
		appointment.rankReason = rankReason;
		return appointment;
	}

	public static Appointment confirmed(SchedulingRequest request, Vet vet, LocalDate date, LocalTime startTime,
			LocalTime endTime, String reason, String changedBy, LocalDate createdDate, LocalTime createdTime) {
		Appointment appointment = confirmed(request.getPet(), vet, date, startTime, endTime, reason, changedBy,
				createdDate, createdTime);
		appointment.request = request;
		return appointment;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public Pet getPet() {
		return this.pet;
	}

	public Vet getVet() {
		return this.vet;
	}

	public LocalDate getDate() {
		return this.date;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public LocalDate getHeldDate() {
		return this.heldDate;
	}

	public LocalTime getHeldTime() {
		return this.heldTime;
	}

	public String getRankReason() {
		return this.rankReason;
	}

	public String getLastChangeReason() {
		return this.lastChangeReason;
	}

	public String getLastChangedBy() {
		return this.lastChangedBy;
	}

	public CancelledBy getCancelledBy() {
		return this.cancelledBy;
	}

	public LocalDate getCancelledDate() {
		return this.cancelledDate;
	}

	public LocalTime getCancelledTime() {
		return this.cancelledTime;
	}

	public LocalDate getCreatedDate() {
		return this.createdDate;
	}

	public LocalTime getCreatedTime() {
		return this.createdTime;
	}

	public void accept() {
		this.status = AppointmentStatus.CONFIRMED;
	}

	public int getDurationMinutes() {
		if (this.startTime == null || this.endTime == null) {
			return 0;
		}
		return (int) java.time.Duration.between(this.startTime, this.endTime).toMinutes();
	}

	public boolean isCancellableByOwnerAt(LocalDate currentDate, LocalTime currentTime) {
		if (this.status != AppointmentStatus.CONFIRMED) {
			return false;
		}
		return currentDate.isBefore(this.date)
				|| (currentDate.isEqual(this.date) && currentTime.isBefore(this.startTime));
	}

	void reschedule(Vet vet, LocalDate date, LocalTime startTime, LocalTime endTime, String reason, String changedBy) {
		this.vet = vet;
		this.date = date;
		this.startTime = startTime;
		this.endTime = endTime;
		this.lastChangeReason = reason;
		this.lastChangedBy = changedBy;
	}

	void cancel(CancelledBy actor, String reason, String changedBy, LocalDate date, LocalTime time) {
		this.status = AppointmentStatus.CANCELLED;
		this.cancelledBy = actor;
		this.cancelledDate = date;
		this.cancelledTime = time;
		this.lastChangeReason = reason;
		this.lastChangedBy = changedBy;
	}

	void complete() {
		this.status = AppointmentStatus.COMPLETED;
	}

	void markNoShow() {
		this.status = AppointmentStatus.NO_SHOW;
	}

}
