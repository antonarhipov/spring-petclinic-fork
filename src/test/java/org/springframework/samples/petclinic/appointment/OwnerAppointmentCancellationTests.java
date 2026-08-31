package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.audit.AuditEventRepository;
import org.springframework.samples.petclinic.audit.OwnerHistoryRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OwnerAppointmentCancellationTests {

	@Autowired
	private OwnerAppointmentService ownerAppointmentService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private CapacityConflictService capacityConflictService;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private ProtectedPayloadRepository payloadRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private Clock clock;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private final Integer vetId = 1;

	private Appointment futureAppointment;

	private Appointment pastAppointment;

	@BeforeEach
	void setUp() {
		Instant now = this.clock.instant();
		Instant futureStart = now.plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
		Instant futureEnd = futureStart.plus(30, ChronoUnit.MINUTES);

		this.futureAppointment = new Appointment(this.ownerId, this.petId, this.vetId, futureStart, futureEnd,
				"America/New_York");
		this.futureAppointment.setBookingState(BookingState.CONFIRMED);
		this.futureAppointment.setOutcomeState(OutcomeState.PENDING);
		this.futureAppointment = this.appointmentRepository.save(this.futureAppointment);

		Instant pastStart = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
		Instant pastEnd = pastStart.plus(30, ChronoUnit.MINUTES);

		this.pastAppointment = new Appointment(this.ownerId, this.petId, this.vetId, pastStart, pastEnd,
				"America/New_York");
		this.pastAppointment.setBookingState(BookingState.CONFIRMED);
		this.pastAppointment.setOutcomeState(OutcomeState.PENDING);
		this.pastAppointment = this.appointmentRepository.save(this.pastAppointment);
	}

	@Test
	void cancellingFutureAppointmentUpdatesBookingStateAndFreesCalendarCapacity() {
		// Verify capacity is blocked before cancellation
		boolean blockedBefore = this.capacityConflictService.hasOverlappingBlocker(this.vetId, this.petId, this.ownerId,
				this.futureAppointment.getStartAt(), this.futureAppointment.getEndAt());
		assertThat(blockedBefore).isTrue();

		String reason = "Out of town on family trip";
		Appointment cancelled = this.ownerAppointmentService.cancelAppointmentByOwner(this.futureAppointment.getId(),
				this.ownerId, reason);

		assertThat(cancelled.getBookingState()).isEqualTo(BookingState.CANCELLED);

		// Verify capacity is freed after cancellation
		boolean blockedAfter = this.capacityConflictService.hasOverlappingBlocker(this.vetId, this.petId, this.ownerId,
				this.futureAppointment.getStartAt(), this.futureAppointment.getEndAt());
		assertThat(blockedAfter).isFalse();

		// Verify change event
		List<AppointmentChangeEvent> changeEvents = this.changeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(this.futureAppointment.getId());
		assertThat(changeEvents).hasSize(1);
		AppointmentChangeEvent change = changeEvents.get(0);
		assertThat(change.getEventType()).isEqualTo("OWNER_CANCELLED");
		assertThat(change.getActorRole()).isEqualTo("ROLE_OWNER");
		assertThat(change.getProtectedReasonPayloadId()).isNotNull();

		// Verify reason payload
		ProtectedPayload payload = this.payloadRepository.findById(change.getProtectedReasonPayloadId()).orElseThrow();
		String decrypted = this.payloadService.decryptToString(payload);
		assertThat(decrypted).isEqualTo(reason);
	}

	@Test
	void cancellingPastAppointmentFails() {
		assertThatThrownBy(() -> this.ownerAppointmentService.cancelAppointmentByOwner(this.pastAppointment.getId(),
				this.ownerId, "Reason"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cannot be cancelled by owner");
	}

	@Test
	void foreignOwnerCannotCancelAppointment() {
		assertThatThrownBy(() -> this.ownerAppointmentService.cancelAppointmentByOwner(this.futureAppointment.getId(),
				999, "Reason"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Appointment not found");
	}

}
