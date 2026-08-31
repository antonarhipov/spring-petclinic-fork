package org.springframework.samples.petclinic.owner;

import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerAccessService {

	private final OwnerRepository ownerRepository;

	private final OwnerHistoryService ownerHistoryService;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final ObjectMapper objectMapper;

	public OwnerAccessService(OwnerRepository ownerRepository, OwnerHistoryService ownerHistoryService,
			ProtectedPayloadService payloadService, AuditService auditService, ObjectMapper objectMapper) {
		this.ownerRepository = Objects.requireNonNull(ownerRepository, "ownerRepository must not be null");
		this.ownerHistoryService = Objects.requireNonNull(ownerHistoryService, "ownerHistoryService must not be null");
		this.payloadService = Objects.requireNonNull(payloadService, "payloadService must not be null");
		this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
	}

	public void verifyOwnerAccess(PetClinicPrincipal principal, Integer requestedOwnerId) {
		if (principal == null) {
			throw new AccessDeniedException("Authentication required");
		}
		if (principal.getRole() == Role.STAFF) {
			return; // Staff can access any owner
		}
		if (principal.getRole() == Role.OWNER) {
			if (principal.getOwnerId() == null || !principal.getOwnerId().equals(requestedOwnerId)) {
				throw new AccessDeniedException("Access denied to owner record: " + requestedOwnerId);
			}
			return;
		}
		throw new AccessDeniedException("Unsupported role: " + principal.getRole());
	}

	@Transactional(readOnly = true)
	public Owner getOwnerProfile(Integer ownerId, PetClinicPrincipal principal) {
		verifyOwnerAccess(principal, ownerId);
		return this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	public Owner updateOwnerContact(Integer ownerId, String address, String city, String telephone,
			PetClinicPrincipal principal) {
		Owner owner = getOwnerProfile(ownerId, principal);
		OwnerProfileForm form = OwnerProfileForm.from(owner);
		form.setAddress(address);
		form.setCity(city);
		form.setTelephone(telephone);
		return updateOwnerProfile(ownerId, form, principal);
	}

	public Owner updateOwnerProfile(Integer ownerId, OwnerProfileForm form, PetClinicPrincipal principal) {
		verifyOwnerAccess(principal, ownerId);
		Objects.requireNonNull(form, "form must not be null");
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));

		Map<String, String> before = profileValues(owner);
		owner.setFirstName(form.getFirstName().trim());
		owner.setLastName(form.getLastName().trim());
		owner.setAddress(form.getAddress().trim());
		owner.setCity(form.getCity().trim());
		owner.setTelephone(form.getTelephone().trim());
		Owner saved = this.ownerRepository.save(owner);
		Map<String, Object> auditValues = Map.of("before", before, "after", profileValues(saved));
		ProtectedPayload auditPayload = this.payloadService.storeJson("OWNER_PROFILE_CHANGE", toJson(auditValues));

		this.ownerHistoryService.recordEvent(ownerId, "PROFILE_UPDATE", "Name or contact information updated", "Owner",
				String.valueOf(ownerId));
		this.auditService.recordEvent(principal.getAccountId(), "OWNER_PROFILE_UPDATED", "Owner", ownerId.toString(),
				"SUCCESS", UUID.randomUUID(), null, auditPayload.getId());

		return saved;
	}

	private Map<String, String> profileValues(Owner owner) {
		return Map.of("firstName", owner.getFirstName(), "lastName", owner.getLastName(), "address", owner.getAddress(),
				"city", owner.getCity(), "telephone", owner.getTelephone());
	}

	private String toJson(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Profile audit payload could not be created", ex);
		}
	}

}
