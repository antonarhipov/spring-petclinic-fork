package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentLifecyclePolicyTests {

	private AppointmentLifecyclePolicy policy;

	private Instant now;

	@BeforeEach
	void setUp() {
		this.policy = new AppointmentLifecyclePolicy();
		this.now = Instant.parse("2026-09-01T10:00:00Z");
	}

	private Appointment createAppointment(BookingState bookingState, OutcomeState outcomeState, Instant startAt) {
		Appointment appointment = new Appointment(1, 1, 1, startAt, startAt.plus(30, ChronoUnit.MINUTES),
				"America/New_York");
		appointment.setBookingState(bookingState);
		appointment.setOutcomeState(outcomeState);
		return appointment;
	}

	@Test
	void canCompleteOnlyConfirmedPendingInPastOrPresent() {
		Appointment pastPending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment presentPending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING, this.now);
		Appointment futurePending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.plus(1, ChronoUnit.HOURS));
		Appointment pastCompleted = createAppointment(BookingState.CONFIRMED, OutcomeState.COMPLETED,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment cancelled = createAppointment(BookingState.CANCELLED, OutcomeState.PENDING,
				this.now.minus(1, ChronoUnit.HOURS));

		assertThat(this.policy.canComplete(pastPending, this.now)).isTrue();
		assertThat(this.policy.canComplete(presentPending, this.now)).isTrue();
		assertThat(this.policy.canComplete(futurePending, this.now)).isFalse();
		assertThat(this.policy.canComplete(pastCompleted, this.now)).isFalse();
		assertThat(this.policy.canComplete(cancelled, this.now)).isFalse();
		assertThat(this.policy.canComplete(null, this.now)).isFalse();
	}

	@Test
	void canRecordNoShowOnlyConfirmedPendingInPastOrPresent() {
		Appointment pastPending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment futurePending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.plus(1, ChronoUnit.HOURS));
		Appointment completed = createAppointment(BookingState.CONFIRMED, OutcomeState.COMPLETED,
				this.now.minus(1, ChronoUnit.HOURS));

		assertThat(this.policy.canRecordNoShow(pastPending, this.now)).isTrue();
		assertThat(this.policy.canRecordNoShow(futurePending, this.now)).isFalse();
		assertThat(this.policy.canRecordNoShow(completed, this.now)).isFalse();
		assertThat(this.policy.canRecordNoShow(null, this.now)).isFalse();
	}

	@Test
	void canCorrectOutcomeWhenConfirmedAndOutcomeNonPending() {
		Appointment pending = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment completed = createAppointment(BookingState.CONFIRMED, OutcomeState.COMPLETED,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment noShow = createAppointment(BookingState.CONFIRMED, OutcomeState.NO_SHOW,
				this.now.minus(1, ChronoUnit.HOURS));
		Appointment cancelled = createAppointment(BookingState.CANCELLED, OutcomeState.COMPLETED,
				this.now.minus(1, ChronoUnit.HOURS));

		assertThat(this.policy.canCorrectOutcome(pending, this.now)).isFalse();
		assertThat(this.policy.canCorrectOutcome(completed, this.now)).isTrue();
		assertThat(this.policy.canCorrectOutcome(noShow, this.now)).isTrue();
		assertThat(this.policy.canCorrectOutcome(cancelled, this.now)).isFalse();
		assertThat(this.policy.canCorrectOutcome(null, this.now)).isFalse();
	}

	@Test
	void canRescheduleOnlyConfirmedPendingInFuture() {
		Appointment future = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.plus(1, ChronoUnit.DAYS));
		Appointment past = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.minus(1, ChronoUnit.DAYS));
		Appointment completed = createAppointment(BookingState.CONFIRMED, OutcomeState.COMPLETED,
				this.now.plus(1, ChronoUnit.DAYS));
		Appointment cancelled = createAppointment(BookingState.CANCELLED, OutcomeState.PENDING,
				this.now.plus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canReschedule(future, this.now)).isTrue();
		assertThat(this.policy.canReschedule(past, this.now)).isFalse();
		assertThat(this.policy.canReschedule(completed, this.now)).isFalse();
		assertThat(this.policy.canReschedule(cancelled, this.now)).isFalse();
		assertThat(this.policy.canReschedule(null, this.now)).isFalse();
	}

	@Test
	void canStaffAndOwnerCancelOnlyConfirmedPendingInFuture() {
		Appointment future = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.plus(2, ChronoUnit.HOURS));
		Appointment past = createAppointment(BookingState.CONFIRMED, OutcomeState.PENDING,
				this.now.minus(2, ChronoUnit.HOURS));
		Appointment cancelled = createAppointment(BookingState.CANCELLED, OutcomeState.PENDING,
				this.now.plus(2, ChronoUnit.HOURS));

		assertThat(this.policy.canStaffCancel(future, this.now)).isTrue();
		assertThat(this.policy.canStaffCancel(past, this.now)).isFalse();
		assertThat(this.policy.canStaffCancel(cancelled, this.now)).isFalse();

		assertThat(this.policy.canOwnerCancel(future, this.now)).isTrue();
		assertThat(this.policy.canOwnerCancel(past, this.now)).isFalse();
		assertThat(this.policy.canOwnerCancel(cancelled, this.now)).isFalse();
	}

}
