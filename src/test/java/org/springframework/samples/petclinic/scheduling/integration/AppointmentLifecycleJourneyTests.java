package org.springframework.samples.petclinic.scheduling.integration;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentCorrectionService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentReasonCategory;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlock;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationResourceType;
import org.springframework.samples.petclinic.scheduling.audit.AuditEvent;
import org.springframework.samples.petclinic.scheduling.audit.AuditEventRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentLifecycleJourneyTests {

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	ReservationBlockRepository blocks;

	@Autowired
	VisitRepository visits;

	@Autowired
	AuditEventRepository audits;

	@Autowired
	AppointmentLifecycleService lifecycle;

	@Autowired
	AppointmentCorrectionService corrections;

	@Autowired
	AvailabilityCommandService availability;

	@Autowired
	AccountRepository accounts;

	@Test
	@Transactional
	void staffRescheduleCompleteAndCorrectionAreAuditedWithOneVisit() {
		Long staffId = this.accounts.findByUsername("admin").orElseThrow().getId();
		for (DayOfWeek day : DayOfWeek.values()) {
			this.availability.addShift(1, day, LocalTime.of(8, 0), LocalTime.of(18, 0), staffId);
		}
		Instant originalStart = Instant.parse("2020-03-16T14:00:00Z");
		Instant originalEnd = Instant.parse("2020-03-16T14:30:00Z");
		Appointment appointment = new Appointment();
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(originalStart);
		appointment.setEndAt(originalEnd);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis("OWNER_ACCEPT");
		appointment.setCreatedAt(originalStart);
		appointment.setUpdatedAt(originalStart);
		appointment = this.appointments.saveAndFlush(appointment);
		saveBlock(ReservationResourceType.VETERINARIAN, 1, originalStart, appointment.getId());
		saveBlock(ReservationResourceType.PET, 1, originalStart, appointment.getId());
		saveBlock(ReservationResourceType.VETERINARIAN, 1, originalStart.plusSeconds(900), appointment.getId());
		saveBlock(ReservationResourceType.PET, 1, originalStart.plusSeconds(900), appointment.getId());

		ZoneId zone = ZoneId.of("America/Chicago");
		ZonedDateTime later = ZonedDateTime.of(2020, 3, 16, 10, 0, 0, 0, zone);
		CandidateSlot slot = new CandidateSlot("1@later", 1, later.toInstant(), later.plusMinutes(30).toInstant(),
				"STAFF", 0);
		Long appointmentId = appointment.getId();
		this.lifecycle.reschedule(appointmentId, slot, staffId, AppointmentReasonCategory.CLINIC_INITIATED, "move");
		assertThat(this.appointments.findById(appointmentId).orElseThrow().getId()).isEqualTo(appointmentId);
		List<ReservationBlock> replaced = this.blocks.findByAppointmentId(appointmentId);
		assertThat(replaced).hasSize(4);
		assertThat(replaced).allMatch(block -> block.getAppointmentId().equals(appointmentId));
		assertThat(replaced).noneMatch(block -> block.getBlockStart().equals(originalStart));

		this.lifecycle.complete(appointmentId, staffId, "Clinical notes");
		assertThat(this.visits.findByAppointmentId(appointmentId)).isPresent();
		assertThat(this.visits.findByAppointmentId(appointmentId).orElseThrow().getVetId()).isEqualTo(1);
		assertThat(this.visits.findAll().stream().filter(visit -> appointmentId.equals(visit.getAppointmentId())))
			.hasSize(1);

		this.corrections.correct(appointmentId, AppointmentStatus.NO_SHOW, staffId, "was actually a no-show");
		assertThat(this.visits.findByAppointmentId(appointmentId)).isEmpty();
		assertThat(this.appointments.findById(appointmentId).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.NO_SHOW);

		assertThat(this.audits.findAll()).extracting(AuditEvent::getAction)
			.contains("RESCHEDULED", "COMPLETED", "CORRECTED");
		assertThat(this.audits.findAll()
			.stream()
			.filter(event -> List.of("RESCHEDULED", "COMPLETED", "CORRECTED").contains(event.getAction())))
			.allMatch(event -> event.getBeforeJson() != null && event.getAfterJson() != null);
	}

	private void saveBlock(ReservationResourceType type, int resourceId, Instant start, Long appointmentId) {
		ReservationBlock block = new ReservationBlock();
		block.setResourceType(type);
		block.setResourceId(resourceId);
		block.setBlockStart(start);
		block.setAppointmentId(appointmentId);
		this.blocks.saveAndFlush(block);
	}

}
