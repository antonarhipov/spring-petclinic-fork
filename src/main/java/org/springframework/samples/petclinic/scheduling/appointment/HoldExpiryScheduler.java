package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpiryScheduler {

	private final HoldExpiryService expiry;

	public HoldExpiryScheduler(HoldExpiryService expiry) {
		this.expiry = expiry;
	}

	@Scheduled(fixedDelayString = "PT15S")
	public void scan() {
		this.expiry.expireDueHolds();
	}

}
