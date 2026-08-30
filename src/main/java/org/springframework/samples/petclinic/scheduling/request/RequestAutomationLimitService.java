package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.stereotype.Service;

@Service
public class RequestAutomationLimitService {

	public static final int MAX_REJECTION_OR_EXPIRY = 5;

	public boolean limitReached(int count) {
		return count >= MAX_REJECTION_OR_EXPIRY;
	}

}
