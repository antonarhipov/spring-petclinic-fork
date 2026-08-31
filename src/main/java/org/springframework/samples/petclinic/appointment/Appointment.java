package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.shared.persistence.SchedulingEntity;

@Entity
@Table(name = "appointments")
public class Appointment extends SchedulingEntity {

	@Column(name = "owner_id", nullable = false)
	private Integer ownerId;

	@Column(name = "pet_id", nullable = false)
	private Integer petId;

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "zone_id", nullable = false, length = 64)
	private String zoneId;

	@Enumerated(EnumType.STRING)
	@Column(name = "booking_state", nullable = false, length = 32)
	private BookingState bookingState = BookingState.CONFIRMED;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome_state", nullable = false, length = 32)
	private OutcomeState outcomeState = OutcomeState.PENDING;

	@Column(name = "originating_request_id")
	private Long originatingRequestId;

	@Column(name = "originating_offer_id")
	private Long originatingOfferId;

	@Column(name = "legacy_visit_id")
	private Integer legacyVisitId;

	public Appointment() {
	}

	public Appointment(Integer ownerId, Integer petId, Integer vetId, Instant startAt, Instant endAt, String zoneId) {
		this.ownerId = ownerId;
		this.petId = petId;
		this.vetId = vetId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.zoneId = zoneId;
		this.bookingState = BookingState.CONFIRMED;
		this.outcomeState = OutcomeState.PENDING;
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

	public long getDurationMinutes() {
		if (this.startAt == null || this.endAt == null) {
			return 0;
		}
		return java.time.Duration.between(this.startAt, this.endAt).toMinutes();
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

	public BookingState getBookingState() {
		return this.bookingState;
	}

	public void setBookingState(BookingState bookingState) {
		this.bookingState = bookingState;
	}

	public OutcomeState getOutcomeState() {
		return this.outcomeState;
	}

	public void setOutcomeState(OutcomeState outcomeState) {
		this.outcomeState = outcomeState;
	}

	public Long getOriginatingRequestId() {
		return this.originatingRequestId;
	}

	public void setOriginatingRequestId(Long originatingRequestId) {
		this.originatingRequestId = originatingRequestId;
	}

	public Long getOriginatingOfferId() {
		return this.originatingOfferId;
	}

	public void setOriginatingOfferId(Long originatingOfferId) {
		this.originatingOfferId = originatingOfferId;
	}

	public Integer getLegacyVisitId() {
		return this.legacyVisitId;
	}

	public void setLegacyVisitId(Integer legacyVisitId) {
		this.legacyVisitId = legacyVisitId;
	}

}
