package org.springframework.samples.petclinic.scheduling.request;

public enum RequestState {

	AWAITING_CONSENT,

	INTERPRETING,

	INTERPRETATION_FAILED,

	INTERPRETED,

	SUGGESTION_OFFERED,

	WITH_STAFF,

	ACCEPTED,

	ABANDONED

}
