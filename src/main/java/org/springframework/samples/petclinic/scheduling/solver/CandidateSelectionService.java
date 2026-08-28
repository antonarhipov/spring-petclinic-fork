package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityQueryService;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettings;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettingsRepository;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@Service
public class CandidateSelectionService {

	private static final Logger logger = LoggerFactory.getLogger(CandidateSelectionService.class);

	private final VetRepository vets;

	private final ClinicSchedulingSettingsRepository settings;

	private final AvailabilityQueryService availability;

	private final AppointmentOfferRepository offers;

	private final ReservationBlockRepository reservations;

	private final Clock clock;

	public CandidateSelectionService(VetRepository vets, ClinicSchedulingSettingsRepository settings,
			AvailabilityQueryService availability, AppointmentOfferRepository offers,
			ReservationBlockRepository reservations, Clock clock) {
		this.vets = vets;
		this.settings = settings;
		this.availability = availability;
		this.offers = offers;
		this.reservations = reservations;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<CandidateSlot> select(RequestRevision revision) {
		Instant started = this.clock.instant();
		ClinicSchedulingSettings clinic = this.settings.findById(1).orElseThrow();
		int duration = revision.getDurationMinutes();
		Instant first = ceilToQuarter(started.plus(Duration.ofMinutes(clinic.getOwnerMinimumNoticeMinutes())));
		Instant limit = first.plus(Duration.ofDays(clinic.getBookingHorizonDays()));
		List<AppointmentOffer> prior = this.offers.findByRevisionId(revision.getId());
		List<Vet> sortedVets = this.vets.findAll().stream().sorted(Comparator.comparing(Vet::getId)).toList();
		for (Instant slot = first; slot.isBefore(limit); slot = slot.plus(Duration.ofMinutes(15))) {
			for (Vet vet : sortedVets) {
				if (requiresUnavailableSpecialty(revision, vet) || excluded(prior, vet, slot)
						|| !this.availability.isAvailable(vet.getId(), slot, duration)
						|| !isFree("VETERINARIAN", vet.getId(), slot, duration)
						|| !isFree("PET", revision.getRequest().getPet().getId(), slot, duration)) {
					continue;
				}
				logger.debug("Candidate selected correlationId={} candidateCount={} score=0 elapsedMs={}",
						revision.getCorrelationId(), 1, Duration.between(started, this.clock.instant()).toMillis());
				return Optional.of(new CandidateSlot(vet, slot));
			}
		}
		logger.debug("No candidate correlationId={} candidateCount=0 elapsedMs={}", revision.getCorrelationId(),
				Duration.between(started, this.clock.instant()).toMillis());
		return Optional.empty();
	}

	private boolean requiresUnavailableSpecialty(RequestRevision revision, Vet vet) {
		String required = revision.getRequiredSpecialty();
		return required != null && !required.isBlank()
				&& vet.getSpecialties().stream().noneMatch(specialty -> required.equalsIgnoreCase(specialty.getName()));
	}

	private boolean excluded(List<AppointmentOffer> prior, Vet vet, Instant slot) {
		return prior.stream()
			.anyMatch(offer -> offer.getVet().getId().equals(vet.getId()) && offer.getStartAt().equals(slot));
	}

	private boolean isFree(String resourceType, Integer resourceId, Instant start, int duration) {
		for (Instant slot = start; slot
			.isBefore(start.plus(Duration.ofMinutes(duration))); slot = slot.plus(Duration.ofMinutes(15))) {
			if (this.reservations.existsByResourceTypeAndResourceIdAndSlotStart(resourceType, resourceId, slot)) {
				return false;
			}
		}
		return true;
	}

	private Instant ceilToQuarter(Instant instant) {
		long epoch = instant.getEpochSecond();
		long quarter = 15 * 60L;
		return Instant.ofEpochSecond(((epoch + quarter - 1) / quarter) * quarter);
	}

}
