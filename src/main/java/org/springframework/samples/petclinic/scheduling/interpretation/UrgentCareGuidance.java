package org.springframework.samples.petclinic.scheduling.interpretation;

public record UrgentCareGuidance(String title, String instructions, String emergencyPhoneNumber,
		String hospitalAddress) {
	public static UrgentCareGuidance defaultGuidance() {
		return new UrgentCareGuidance("Urgent / Emergency Care Notice",
				"If your pet is experiencing a medical emergency (e.g. heavy bleeding, difficulty breathing, collapse, suspected poisoning), please do not wait for an online appointment. Contact our emergency department or visit the clinic immediately.",
				"(555) 019-2834", "110 W. Liberty St, Madison, WI");
	}
}
