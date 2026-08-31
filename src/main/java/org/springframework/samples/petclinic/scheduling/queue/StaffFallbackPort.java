package org.springframework.samples.petclinic.scheduling.queue;

import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;

public interface StaffFallbackPort {

	void sendToFallbackQueue(Long requestId, String fallbackReason, Urgency urgency, String note);

}
