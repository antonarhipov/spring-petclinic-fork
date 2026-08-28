package org.springframework.samples.petclinic.scheduling.offer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OfferExpirationScheduler {

	private final OfferExpiryService expirations;

	OfferExpirationScheduler(OfferExpiryService expirations) {
		this.expirations = expirations;
	}

	@Scheduled(fixedDelayString = "${scheduling.offer-expiration-delay-ms:60000}")
	void expireOffers() {
		this.expirations.expireOutstanding();
	}

}
