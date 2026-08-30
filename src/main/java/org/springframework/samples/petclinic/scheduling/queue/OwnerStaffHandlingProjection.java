package org.springframework.samples.petclinic.scheduling.queue;

public record OwnerStaffHandlingProjection(String status, String ownerStatusCode) {

	public static OwnerStaffHandlingProjection of() {
		return new OwnerStaffHandlingProjection("STAFF_HANDLING", "STAFF_HANDLING");
	}

}
