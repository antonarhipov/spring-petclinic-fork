package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.queue.QueueDirectBookingService.QueueDirectBookReview;
import org.springframework.samples.petclinic.security.WebMvcPetClinicSecurity;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcPetClinicSecurity
@WebMvcTest(StaffQueueResolutionController.class)
class StaffQueueResolutionControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StaffQueueQueryService queryService;

	@MockitoBean
	private AssistedOfferService assistedOfferService;

	@MockitoBean
	private QueueDirectBookingService directBookingService;

	@MockitoBean
	private QueueContactService contactService;

	@MockitoBean
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private StaffSuggestionService suggestionService;

	private StaffQueueQueryService.QueueItemDetailDto detail;

	@BeforeEach
	void setUp() {
		this.detail = new StaffQueueQueryService.QueueItemDetailDto(7L, 11L, 1, "Leo", "Cat", 1, "George Franklin",
				"555-0100", "1 Main Street", "Tallinn", null, "NO_MATCH", QueueState.IN_REVIEW, null, 1L, "admin",
				Instant.parse("2026-08-31T09:00:00Z"), Instant.parse("2026-08-31T09:00:00Z"), "Routine check", false,
				2L, 3L, 1, 10L, 1, "Routine check", 30, 1, "Dr. Carter", null, null, List.of(), List.of(), List.of(),
				List.of(), List.of());
		given(this.queryService.getQueueItemDetail(7L)).willReturn(Optional.of(this.detail));
		given(this.effectiveAvailabilityService.getClinicZoneId()).willReturn(ZoneId.of("Europe/Tallinn"));
	}

	@Test
	void firstSubmissionRendersReviewWithoutBooking() throws Exception {
		given(this.directBookingService.reviewDirectBooking(any()))
			.willReturn(new QueueDirectBookReview(7L, "George Franklin", "Leo", "James Carter",
					Instant.parse("2026-09-07T07:00:00Z"), Instant.parse("2026-09-07T07:30:00Z"), "Europe/Tallinn",
					"PHONE", "OWNER_REQUEST", "Requested by owner"));

		this.mockMvc
			.perform(post("/staff/queue/7/direct-book").with(csrf())
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("reasonCategory", "OWNER_REQUEST")
				.param("internalReason", "Requested by owner")
				.param("expectedRequestVersion", "2")
				.param("expectedWorkflowRevision", "1")
				.param("expectedQueueVersion", "3"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/queue/direct-book-review"))
			.andExpect(model().attributeExists("review", "directBookForm"));

		verify(this.directBookingService).reviewDirectBooking(any());
		verify(this.directBookingService, never()).directBookFromQueue(any());
	}

	@Test
	void confirmedReviewCommitsAndRedirects() throws Exception {
		Appointment appointment = new Appointment();
		appointment.setId(42L);
		given(this.directBookingService.directBookFromQueue(any())).willReturn(appointment);

		this.mockMvc
			.perform(post("/staff/queue/7/direct-book").with(csrf())
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("reasonCategory", "OWNER_REQUEST")
				.param("internalReason", "Requested by owner")
				.param("expectedRequestVersion", "2")
				.param("expectedWorkflowRevision", "1")
				.param("expectedQueueVersion", "3")
				.param("confirmed", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/42"));

		verify(this.directBookingService).directBookFromQueue(any());
	}

}
