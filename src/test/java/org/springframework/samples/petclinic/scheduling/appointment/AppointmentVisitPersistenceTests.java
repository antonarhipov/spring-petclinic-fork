package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AppointmentVisitPersistenceTests {

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	VisitRepository visits;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void appointmentIdIsUniqueOnVisitsAndStoresCompletionDate() {
		Appointment appointment = persistAppointment();
		Visit visit = new Visit();
		visit.setPetId(1);
		visit.setAppointmentId(appointment.getId());
		visit.setVetId(1);
		visit.setDate(LocalDate.parse("2026-03-16"));
		visit.setDescription("Exam complete");
		this.visits.saveAndFlush(visit);
		assertThat(this.visits.findByAppointmentId(appointment.getId())).isPresent();
		assertThat(this.visits.findByAppointmentId(appointment.getId()).orElseThrow().getDate())
			.isEqualTo(LocalDate.parse("2026-03-16"));
		assertThatThrownBy(() -> this.jdbc.update(
				"insert into visits (pet_id, visit_date, description, appointment_id, vet_id) values (1, '2026-03-16', 'dup', ?, 1)",
				appointment.getId()))
			.isInstanceOf(Exception.class);
		assertThat(this.jdbc.queryForObject("select count(*) from visits where appointment_id = ?", Integer.class,
				appointment.getId()))
			.isEqualTo(1);
	}

	private Appointment persistAppointment() {
		Appointment appointment = new Appointment();
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis("OWNER_ACCEPT");
		appointment.setCreatedAt(Instant.parse("2026-03-16T12:00:00Z"));
		appointment.setUpdatedAt(Instant.parse("2026-03-16T12:00:00Z"));
		return this.appointments.saveAndFlush(appointment);
	}

}
