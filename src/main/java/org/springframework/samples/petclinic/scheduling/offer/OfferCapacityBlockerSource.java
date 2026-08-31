package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.availability.CapacityBlockerSource;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.stereotype.Component;

@Component
public class OfferCapacityBlockerSource implements CapacityBlockerSource {

	private final OfferRepository offerRepository;

	private final Clock clock;

	public OfferCapacityBlockerSource(OfferRepository offerRepository, Clock clock) {
		this.offerRepository = offerRepository;
		this.clock = clock;
	}

	@Override
	public List<TimeInterval> findVetBlockers(Integer vetId, Instant startAt, Instant endAt) {
		Instant now = this.clock.instant();
		return this.offerRepository.findActiveHoldsForVet(vetId, now)
			.stream()
			.filter(offer -> offer.getStartAt().isBefore(endAt) && offer.getEndAt().isAfter(startAt))
			.map(offer -> new TimeInterval(offer.getStartAt(), offer.getEndAt()))
			.collect(Collectors.toList());
	}

	@Override
	public List<TimeInterval> findPetBlockers(Integer petId, Instant startAt, Instant endAt) {
		Instant now = this.clock.instant();
		return this.offerRepository.findActiveHoldsForPet(petId, now)
			.stream()
			.filter(offer -> offer.getStartAt().isBefore(endAt) && offer.getEndAt().isAfter(startAt))
			.map(offer -> new TimeInterval(offer.getStartAt(), offer.getEndAt()))
			.collect(Collectors.toList());
	}

	@Override
	public List<TimeInterval> findOwnerBlockers(Integer ownerId, Instant startAt, Instant endAt) {
		Instant now = this.clock.instant();
		return this.offerRepository.findActiveHoldsForOwner(ownerId, now)
			.stream()
			.filter(offer -> offer.getStartAt().isBefore(endAt) && offer.getEndAt().isAfter(startAt))
			.map(offer -> new TimeInterval(offer.getStartAt(), offer.getEndAt()))
			.collect(Collectors.toList());
	}

}
