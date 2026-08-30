package org.springframework.samples.petclinic.scheduling.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.scheduling.availability.ClinicPolicyService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicCapacityJourneyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AvailabilityCommandService commands;

	@Autowired
	ClinicPolicyService policies;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	HoldRepository holds;

	@Autowired
	OfferRepository offers;

	@Autowired
	ReservationBlockRepository blocks;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void availabilityAndSettingsPagesAreStaffOnlyAndConflictsBlockLeave() throws Exception {
		this.mockMvc.perform(get("/staff/availability")).andExpect(status().isOk());
		this.mockMvc.perform(get("/staff/settings")).andExpect(status().isOk());
		Appointment appointment = persistAppointment(Instant.parse("2031-03-17T14:00:00Z"));
		this.mockMvc
			.perform(post("/staff/availability/leave").with(csrf())
				.param("veterinarianId", "1")
				.param("startLocalDate", "2031-03-17")
				.param("endLocalDate", "2031-03-17")
				.param("expectedVersion", String.valueOf(this.policies.current().getVersion())))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/capacity-conflict"));
		assertThatThrownBy(() -> this.commands.addLeave(1, LocalDate.parse("2031-03-17"), LocalDate.parse("2031-03-17"),
				this.accounts.findByUsername("admin").orElseThrow().getId()))
			.isInstanceOf(AvailabilityConflictException.class);
		assertThat(this.appointments.findById(appointment.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void dateExceptionCanDeliberatelyReleaseHold() {
		SeededHold seeded = persistActiveHold(Instant.parse("2031-03-18T14:00:00Z"));
		this.commands.addDateException(1, LocalDate.parse("2031-03-18"), List.of(),
				this.accounts.findByUsername("admin").orElseThrow().getId(), true);
		assertThat(this.holds.findById(seeded.holdId()).orElseThrow().getState()).isEqualTo(HoldStatus.RELEASED);
		assertThat(this.holds.findById(seeded.holdId()).orElseThrow().getReleaseReason()).isEqualTo("CAPACITY_CHANGE");
		assertThat(this.offers.findById(seeded.offerId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.EXPIRED);
		assertThat(this.blocks.findByHoldId(seeded.holdId())).isEmpty();
	}

	private Appointment persistAppointment(Instant start) {
		Appointment appointment = new Appointment();
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(start);
		appointment.setEndAt(start.plusSeconds(1800));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis("OWNER_ACCEPT");
		appointment.setCreatedAt(start);
		appointment.setUpdatedAt(start);
		return this.appointments.saveAndFlush(appointment);
	}

	private SeededHold persistActiveHold(Instant start) {
		Timestamp ts = Timestamp.from(start);
		this.jdbc.update(
				"""
						insert into scheduling_requests (owner_id, pet_id, state, owner_status_code, suspected_emergency, created_at, updated_at, version)
						values (1, 1, 'OFFER_HELD', 'OFFER_HELD', false, ?, ?, 0)
						""",
				ts, ts);
		Long requestId = this.jdbc.queryForObject("select max(id) from scheduling_requests", Long.class);
		this.jdbc.update(
				"""
						insert into request_text_revisions (request_id, sequence, source_text, source_hash, submitted_at, clinic_zone_id, emergency_screen_version)
						values (?, 1, 'hold seed', 'hash', ?, 'America/Chicago', 'none')
						""",
				requestId, ts);
		Long textId = this.jdbc.queryForObject("select max(id) from request_text_revisions", Long.class);
		this.jdbc.update("""
				insert into interpretation_records (text_revision_id, origin, schema_version, outcome, created_at)
				values (?, 'LLM', '1.0', 'VALID', ?)
				""", textId, ts);
		Long interpretationId = this.jdbc.queryForObject("select max(id) from interpretation_records", Long.class);
		this.jdbc.update(
				"""
						insert into request_revisions (request_id, sequence, interpretation_id, status, visit_reason, duration_minutes,
						care_type, urgency, veterinarian_preference_strength, clinic_policy_version, clinic_zone_id, created_at, version)
						values (?, 1, ?, 'CONFIRMED', 'exam', 30, 'GENERAL', 'NO_CONCERN_IDENTIFIED', 'NONE', 1, 'America/Chicago', ?, 0)
						""",
				requestId, interpretationId, ts);
		Long revisionId = this.jdbc.queryForObject("select max(id) from request_revisions", Long.class);
		Offer offer = new Offer();
		offer.setRequestRevisionId(revisionId);
		offer.setVeterinarianId(1);
		offer.setStartAt(start);
		offer.setEndAt(start.plusSeconds(1800));
		offer.setDurationMinutes(30);
		offer.setSource("TIMEFOLD");
		offer.setClassification("PREFERRED");
		offer.setPublicExplanationCode("PREFERRED");
		offer.setStatus(OfferStatus.HELD);
		offer.setExpiresAt(start.plusSeconds(900));
		offer.setCreatedAt(start);
		offer = this.offers.saveAndFlush(offer);
		Hold hold = new Hold();
		hold.setOfferId(offer.getId());
		hold.setRequestId(requestId);
		hold.setState(HoldStatus.ACTIVE);
		hold.setExpiresAt(start.plusSeconds(900));
		hold.setCreatedAt(start);
		hold = this.holds.saveAndFlush(hold);
		this.jdbc.update("""
				insert into reservation_blocks (resource_type, resource_id, block_start, hold_id)
				values ('VETERINARIAN', 1, ?, ?)
				""", ts, hold.getId());
		return new SeededHold(hold.getId(), offer.getId());
	}

	private record SeededHold(Long holdId, Long offerId) {
	}

}
