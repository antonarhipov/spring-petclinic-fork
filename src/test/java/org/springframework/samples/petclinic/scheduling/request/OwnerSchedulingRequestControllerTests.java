package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.security.SecurityTestPrincipals;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MVC tests for {@link OwnerSchedulingRequestController} using the full Spring Security
 * filter chain (proven pattern from {@code SessionLifecycleTests}) rather than
 * {@code @WebMvcTest}, since {@code /owner/**} routes require an authenticated
 * {@link PetClinicPrincipal} with {@code ROLE_OWNER}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerSchedulingRequestControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SchedulingRequestService requestService;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private PetClinicPrincipal george;

	@BeforeEach
	void setUp() {
		Account account = this.accountRepository.findByUsername("george").orElseGet(() -> {
			Account newAccount = new Account();
			newAccount.setUsername("george");
			newAccount.setPasswordHash(this.passwordEncoder.encode("george123"));
			newAccount.setRole(Role.OWNER);
			newAccount.setOwnerId(1);
			newAccount.setEnabled(true);
			newAccount.setPasswordChangeRequired(false);
			newAccount.setSessionVersion(0L);
			return this.accountRepository.save(newAccount);
		});
		this.george = SecurityTestPrincipals.owner(account.getId(), account.getUsername(), account.getOwnerId(),
				account.getSessionVersion(), false);
	}

	private Owner sampleOwner() {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");
		owner.setAddress("110 W. Liberty St.");
		owner.setCity("Madison");
		owner.setTelephone("6085551023");

		Pet pet = new Pet();
		pet.setId(1);
		pet.setName("Leo");
		PetType cat = new PetType();
		cat.setName("cat");
		pet.setType(cat);
		pet.setBirthDate(LocalDate.now().minusYears(3));
		owner.addPet(pet);

		return owner;
	}

	@Test
	void dashboardRequiresAuthentication() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard")).andExpect(status().is3xxRedirection());
	}

	@Test
	void dashboardDisplaysOwnerData() throws Exception {
		Owner owner = sampleOwner();
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(owner));
		given(this.requestService.getOwnerRequests(1)).willReturn(List.of());
		given(this.appointmentRepository.findByOwnerIdOrderByStartAtDesc(1)).willReturn(List.of());

		this.mockMvc.perform(get("/owner/dashboard").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/dashboard"))
			.andExpect(model().attributeExists("owner", "requests", "appointments"));
	}

	@Test
	void initNewRequestFormDisplaysForm() throws Exception {
		Owner owner = sampleOwner();
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(owner));

		this.mockMvc.perform(get("/owner/requests/new").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/new"))
			.andExpect(model().attributeExists("owner", "pets", "form"));
	}

	@Test
	void processNewRequestSuccessRedirectsToDetail() throws Exception {
		Owner owner = sampleOwner();
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(owner));

		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(100L);
		given(this.requestService.submitRequest(eq(1), eq(1), any(), anyBoolean())).willReturn(request);

		this.mockMvc
			.perform(post("/owner/requests").with(user(this.george))
				.with(csrf())
				.param("petId", "1")
				.param("prose", "Leo needs a vaccination.")
				.param("aiConsent", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/requests/100"));
	}

	@Test
	void processNewRequestValidationErrorReturnsForm() throws Exception {
		Owner owner = sampleOwner();
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(owner));

		this.mockMvc
			.perform(post("/owner/requests").with(user(this.george))
				.with(csrf())
				.param("petId", "1")
				.param("prose", "") // blank prose triggers validation failure
				.param("aiConsent", "true"))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/new"))
			.andExpect(model().attributeExists("owner", "pets"));
	}

	@Test
	void requestDetailDisplaysProjection() throws Exception {
		OwnerRequestProjection projection = new OwnerRequestProjection(100L, 1, "Leo",
				RequestState.AWAITING_INTERPRETATION, "INTERPRETING_REQUEST", "We are interpreting your request...",
				null, null, null, Instant.now(), Instant.now());
		given(this.requestService.getOwnerRequestProjection(100L, 1)).willReturn(Optional.of(projection));

		this.mockMvc.perform(get("/owner/requests/100").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/detail"))
			.andExpect(model().attributeExists("request"));
	}

	@Test
	void requestStatusReturnsJsonPayload() throws Exception {
		RequestStatusResponse statusResponse = new RequestStatusResponse(100L, 1, "INTERPRETING_REQUEST",
				"/owner/requests/100", null, Instant.now().toString(), Instant.now().plusSeconds(1800).toString(),
				Instant.now().plusSeconds(1500).toString(), null, false, false, 2000);
		given(this.requestService.getOwnerRequestStatus(100L, 1)).willReturn(Optional.of(statusResponse));

		this.mockMvc.perform(get("/owner/requests/100/status").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value(100))
			.andExpect(jsonPath("$.displayState").value("INTERPRETING_REQUEST"))
			.andExpect(jsonPath("$.pollAfterMillis").value(2000));
	}

	@Test
	void requestStatusReturnsNotFoundForUnknownRequest() throws Exception {
		given(this.requestService.getOwnerRequestStatus(999L, 1)).willReturn(Optional.empty());

		this.mockMvc.perform(get("/owner/requests/999/status").with(user(this.george)))
			.andExpect(status().isNotFound());
	}

}
