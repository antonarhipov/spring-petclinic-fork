package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OfferExpiryWorker {

	private static final Logger log = LoggerFactory.getLogger(OfferExpiryWorker.class);

	private final OfferRepository offerRepository;

	private final OfferLifecycleService offerLifecycleService;

	private final Clock clock;

	public OfferExpiryWorker(OfferRepository offerRepository, OfferLifecycleService offerLifecycleService,
			Clock clock) {
		this.offerRepository = offerRepository;
		this.offerLifecycleService = offerLifecycleService;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(fixedDelay = 2000)
	public int checkAndExpireOffers() {
		Instant now = this.clock.instant();
		List<Offer> expiredHolds = this.offerRepository.findExpiredHolds(now);
		int count = 0;
		for (Offer offer : expiredHolds) {
			try {
				this.offerLifecycleService.expireOffer(offer.getId());
				count++;
			}
			catch (Exception ex) {
				log.error("Failed to expire offer {}: {}", offer.getId(), ex.getMessage(), ex);
			}
		}
		if (count > 0) {
			log.info("Expired {} held offers at {}", count, now);
		}
		return count;
	}

}
