package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.security.WebMvcPetClinicSecurity;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;

@WebMvcPetClinicSecurity
@WebMvcTest(StaffDirectBookingController.class)
class StaffDirectBookingControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DirectBookingService directBookingService;

	@MockitoBean
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@MockitoBean
	private AppointmentRepository appointmentRepository;

	@MockitoBean
	private AppointmentChangeEventRepository appointmentChangeEventRepository;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private AppointmentLifecyclePolicy lifecyclePolicy;

	@MockitoBean
	private Clock clock;

	private Owner sampleOwner() {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");
		owner.setCity("Madison");
		owner.setTelephone("6085551023");

		Pet pet = new Pet();
		pet.setName("Leo");
		PetType cat = new PetType();
		cat.setName("cat");
		pet.setType(cat);
		pet.setBirthDate(LocalDate.now());
		owner.addPet(pet);
		pet.setId(1);
		return owner;
	}

	private Vet sampleVet() {
		Vet vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");
		return vet;
	}

	@Test
	void directBookFormReturnsView() throws Exception {
		ClinicPolicy policy = new ClinicPolicy();
		given(this.effectiveAvailabilityService.getClinicPolicy()).willReturn(policy);
		given(this.ownerRepository.findAll()).willReturn(List.of(sampleOwner()));
		given(this.vetRepository.findAll()).willReturn(List.of(sampleVet()));

		this.mockMvc.perform(get("/staff/appointments/direct-book"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/direct-book"))
			.andExpect(model().attributeExists("allOwners", "allVets", "allowedDurations", "directBookingForm"));
	}

	@Test
	void reviewDirectBookingValidReturnsReviewView() throws Exception {
		ClinicPolicy policy = new ClinicPolicy();
		given(this.effectiveAvailabilityService.getClinicPolicy()).willReturn(policy);
		given(this.effectiveAvailabilityService.getClinicZoneId()).willReturn(ZoneId.of("Europe/Amsterdam"));
		given(this.directBookingService.validateDirectBooking(any())).willReturn(BookingConflictCheck.ok());
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(sampleOwner()));
		given(this.vetRepository.findById(1)).willReturn(Optional.of(sampleVet()));

		this.mockMvc
			.perform(post("/staff/appointments/direct-book").with(csrf())
				.param("ownerId", "1")
				.param("petId", "1")
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("internalReason", "Routine Checkup"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/direct-book-review"))
			.andExpect(model().attributeExists("form", "owner", "pet", "vet", "startAt", "endAt"));
	}

	@Test
	void reviewDirectBookingMissingInternalReasonReturnsFormWithError() throws Exception {
		ClinicPolicy policy = new ClinicPolicy();
		given(this.effectiveAvailabilityService.getClinicPolicy()).willReturn(policy);
		given(this.ownerRepository.findAll()).willReturn(List.of(sampleOwner()));
		given(this.vetRepository.findAll()).willReturn(List.of(sampleVet()));

		this.mockMvc
			.perform(post("/staff/appointments/direct-book").with(csrf())
				.param("ownerId", "1")
				.param("petId", "1")
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("internalReason", ""))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/direct-book"))
			.andExpect(model().attributeExists("errorMessage"));
	}

	@Test
	void reviewDirectBookingConflictReturnsFormWithError() throws Exception {
		ClinicPolicy policy = new ClinicPolicy();
		given(this.effectiveAvailabilityService.getClinicPolicy()).willReturn(policy);
		given(this.effectiveAvailabilityService.getClinicZoneId()).willReturn(ZoneId.of("Europe/Amsterdam"));
		given(this.directBookingService.validateDirectBooking(any()))
			.willReturn(BookingConflictCheck.failed("Veterinarian not available at selected time"));
		given(this.ownerRepository.findAll()).willReturn(List.of(sampleOwner()));
		given(this.vetRepository.findAll()).willReturn(List.of(sampleVet()));

		this.mockMvc
			.perform(post("/staff/appointments/direct-book").with(csrf())
				.param("ownerId", "1")
				.param("petId", "1")
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("internalReason", "Checkup"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/direct-book"))
			.andExpect(model().attributeExists("errorMessage"));
	}

	@Test
	void confirmDirectBookingCreatesAppointmentAndRedirects() throws Exception {
		given(this.effectiveAvailabilityService.getClinicZoneId()).willReturn(ZoneId.of("Europe/Amsterdam"));
		Appointment appointment = new Appointment();
		appointment.setId(42L);
		given(this.directBookingService.bookDirectly(any())).willReturn(appointment);

		this.mockMvc
			.perform(post("/staff/appointments/direct-book-review").with(csrf())
				.param("ownerId", "1")
				.param("petId", "1")
				.param("vetId", "1")
				.param("date", "2026-09-07")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("ownerAgreementRecorded", "true")
				.param("agreementMedium", "PHONE")
				.param("internalReason", "Routine Checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/42"));

		verify(this.directBookingService).bookDirectly(any());
	}

	@Test
	void viewAppointmentDetailsReturnsDetailView() throws Exception {
		Instant now = Instant.parse("2026-08-31T10:00:00Z");
		Appointment appointment = new Appointment(1, 1, 1, now.plusSeconds(3600), now.plusSeconds(5400),
				"Europe/Amsterdam");
		appointment.setId(10L);
		given(this.appointmentRepository.findById(10L)).willReturn(Optional.of(appointment));
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(sampleOwner()));
		given(this.vetRepository.findById(1)).willReturn(Optional.of(sampleVet()));
		given(this.appointmentChangeEventRepository.findByAppointmentIdOrderByOccurredAtAsc(10L)).willReturn(List.of());
		given(this.effectiveAvailabilityService.getClinicZoneId()).willReturn(ZoneId.of("Europe/Amsterdam"));
		given(this.clock.instant()).willReturn(now);
		given(this.lifecyclePolicy.canStaffCancel(appointment, now)).willReturn(true);
		given(this.lifecyclePolicy.canReschedule(appointment, now)).willReturn(true);

		this.mockMvc.perform(get("/staff/appointments/10"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/detail"))
			.andExpect(model().attributeExists("appointment", "owner", "pet", "vet", "changeEvents", "canCancel",
					"canReschedule", "canComplete", "canRecordNoShow", "canCorrectOutcome"))
			.andExpect(content().string(containsString("href=\"/staff/appointments/10/cancel\"")));
	}

}
