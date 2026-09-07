package org.springframework.samples.petclinic.scheduling.matching;

public enum RankReason {

	PREFERRED_WINDOW_PREFERRED_VET("scheduling.slot.rank.preferred.honored"),
	PREFERRED_WINDOW_PREFERRED_VET_UNAVAILABLE("scheduling.slot.rank.preferred.unavailable"),
	PREFERRED_WINDOW_NO_VET_REQUESTED("scheduling.slot.rank.preferred.noPreference"),
	ALLOWED_WINDOW_PREFERRED_VET("scheduling.slot.rank.allowed.honored"),
	ALLOWED_WINDOW_PREFERRED_VET_UNAVAILABLE("scheduling.slot.rank.allowed.unavailable"),
	ALLOWED_WINDOW_NO_VET_REQUESTED("scheduling.slot.rank.allowed.noPreference"),
	STAFF_SELECTED("scheduling.slot.rank.staff");

	private final String messageKey;

	RankReason(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return this.messageKey;
	}

	public static RankReason forSlot(Slot slot, Integer preferredVetId, boolean preferredVetEligible) {
		boolean isPreferredWindow = slot.windowType() == WindowType.PREFERRED;
		if (preferredVetId == null) {
			return isPreferredWindow ? PREFERRED_WINDOW_NO_VET_REQUESTED : ALLOWED_WINDOW_NO_VET_REQUESTED;
		}
		if (preferredVetEligible && slot.vetId() == preferredVetId) {
			return isPreferredWindow ? PREFERRED_WINDOW_PREFERRED_VET : ALLOWED_WINDOW_PREFERRED_VET;
		}
		return isPreferredWindow ? PREFERRED_WINDOW_PREFERRED_VET_UNAVAILABLE
				: ALLOWED_WINDOW_PREFERRED_VET_UNAVAILABLE;
	}

}
