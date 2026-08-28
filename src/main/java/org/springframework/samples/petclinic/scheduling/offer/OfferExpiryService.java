package org.springframework.samples.petclinic.scheduling.offer;

import org.springframework.stereotype.Service;

@Service
public class OfferExpiryService {

	private final OfferService offers;

	public OfferExpiryService(OfferService offers) {
		this.offers = offers;
	}

	public void expireOutstanding() {
		this.offers.expireOutstanding(null);
	}

}
