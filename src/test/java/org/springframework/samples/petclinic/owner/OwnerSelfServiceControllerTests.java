package org.springframework.samples.petclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.SecurityConfig;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;

@WebMvcTest(OwnerSelfServiceController.class)
@Import(WebMvcSecurityTestConfig.class)
class OwnerSelfServiceControllerTests {

	private static final int OWNER_ID = 1;

	private static final int PET_ID = 7;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private PetRepository petRepository;

	@MockitoBean
	private PetTypeRepository petTypeRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal ownerPrincipal;

	private Owner owner;

	@BeforeEach
	void setUp() {
		owner = new Owner();
		owner.setId(OWNER_ID);
		owner.setFirstName("George");
		owner.setLastName("Franklin");
		owner.setAddress("110 W. Liberty St.");
		owner.setCity("Madison");
		owner.setTelephone("6085551023");

		Pet pet = new Pet();
		pet.setId(PET_ID);
		pet.setName("Leo");
		pet.setBirthDate(LocalDate.of(2010, 9, 7));
		PetType cat = new PetType();
		cat.setId(1);
		cat.setName("cat");
		pet.setType(cat);
		owner.addPet(pet);

		ownerPrincipal = new UserPrincipal(100, "owner1", "hashed", AccountRole.OWNER, OWNER_ID, false, true, null);

		given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner));
		given(petRepository.findByOwnerId(OWNER_ID)).willReturn(List.of(pet));
		given(petRepository.findByIdAndOwnerId(PET_ID, OWNER_ID)).willReturn(Optional.of(pet));
		given(petRepository.findByIdAndOwnerId(eq(999), eq(OWNER_ID))).willReturn(Optional.empty());
		given(petTypeRepository.findPetTypes()).willReturn(List.of(cat));
		given(petTypeRepository.findById(1)).willReturn(Optional.of(cat));
	}

	@Test
	void showMyProfileForAuthenticatedOwner() throws Exception {
		mockMvc.perform(get("/my-profile").with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/myProfile"))
			.andExpect(model().attributeExists("owner"))
			.andExpect(model().attributeExists("pets"));
	}

	@Test
	void updateOwnedProfileSucceeds() throws Exception {
		mockMvc
			.perform(post("/my-profile/edit").with(user(ownerPrincipal))
				.with(csrf())
				.param("firstName", "Georgina")
				.param("lastName", "Franklin")
				.param("address", "110 W. Liberty St.")
				.param("city", "Madison")
				.param("telephone", "6085551023"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-profile"));
		verify(ownerRepository).save(any(Owner.class));
	}

	@Test
	void foreignPetIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/my-pets/{petId}/edit", 999).with(user(ownerPrincipal))).andExpect(status().isNotFound());
		verify(petRepository, never()).save(any());
	}

	@Test
	void staffCannotAccessOwnerSelfService() throws Exception {
		mockMvc.perform(get("/my-profile").with(user("staff1").roles("STAFF"))).andExpect(status().isForbidden());
	}

	@Test
	void createOwnedPetUsesOwnerLinkage() throws Exception {
		mockMvc
			.perform(post("/my-pets/new").with(user(ownerPrincipal))
				.with(csrf())
				.param("name", "Milo")
				.param("birthDate", "2020-01-01")
				.param("typeId", "1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-profile"));
		verify(ownerRepository).save(any(Owner.class));
		verify(petRepository, never()).save(any());
	}

}
