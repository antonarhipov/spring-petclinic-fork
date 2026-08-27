package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import org.springframework.samples.petclinic.scheduling.model.CareType;

public class SchedulingRequestFact {

	private final Integer requestId;

	private final Integer ownerId;

	private final Integer petId;

	private final CareType careType;

	private final Integer requiredSpecialtyId;

	private final Integer preferredVetId;

	private final Integer durationMinutes;

	private final Instant ownerHorizonStart;

	private final Instant ownerHorizonEnd;

	private final Instant preferredStartWindow;

	private final Instant preferredEndWindow;

	public SchedulingRequestFact(Integer requestId, Integer ownerId, Integer petId, CareType careType,
			Integer requiredSpecialtyId, Integer preferredVetId, Integer durationMinutes, Instant ownerHorizonStart,
			Instant ownerHorizonEnd, Instant preferredStartWindow, Instant preferredEndWindow) {
		this.requestId = requestId;
		this.ownerId = ownerId;
		this.petId = petId;
		this.careType = careType;
		this.requiredSpecialtyId = requiredSpecialtyId;
		this.preferredVetId = preferredVetId;
		this.durationMinutes = durationMinutes;
		this.ownerHorizonStart = ownerHorizonStart;
		this.ownerHorizonEnd = ownerHorizonEnd;
		this.preferredStartWindow = preferredStartWindow;
		this.preferredEndWindow = preferredEndWindow;
	}

	public Integer getRequestId() {
		return requestId;
	}

	public Integer getOwnerId() {
		return ownerId;
	}

	public Integer getPetId() {
		return petId;
	}

	public CareType getCareType() {
		return careType;
	}

	public Integer getRequiredSpecialtyId() {
		return requiredSpecialtyId;
	}

	public Integer getPreferredVetId() {
		return preferredVetId;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public Instant getOwnerHorizonStart() {
		return ownerHorizonStart;
	}

	public Instant getOwnerHorizonEnd() {
		return ownerHorizonEnd;
	}

	public Instant getPreferredStartWindow() {
		return preferredStartWindow;
	}

	public Instant getPreferredEndWindow() {
		return preferredEndWindow;
	}

}
