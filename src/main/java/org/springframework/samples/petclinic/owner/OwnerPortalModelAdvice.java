package org.springframework.samples.petclinic.owner;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = { "org.springframework.samples.petclinic.owner",
		"org.springframework.samples.petclinic.scheduling.request",
		"org.springframework.samples.petclinic.scheduling.offer", "org.springframework.samples.petclinic.appointment" })
public class OwnerPortalModelAdvice {

	private final ObjectProvider<ClinicPolicyRepository> clinicPolicyRepositoryProvider;

	public OwnerPortalModelAdvice(ObjectProvider<ClinicPolicyRepository> clinicPolicyRepositoryProvider) {
		this.clinicPolicyRepositoryProvider = clinicPolicyRepositoryProvider;
	}

	@ModelAttribute("clinicPolicy")
	ClinicPolicy clinicPolicy() {
		ClinicPolicyRepository repository = this.clinicPolicyRepositoryProvider.getIfAvailable();
		return repository != null ? repository.findSingleton().orElseGet(ClinicPolicy::createDefaultPolicy)
				: ClinicPolicy.createDefaultPolicy();
	}

}
