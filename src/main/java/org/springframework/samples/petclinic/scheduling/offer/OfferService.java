package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentSource;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettingsRepository;
import org.springframework.samples.petclinic.scheduling.queue.FallbackQueueService;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestState;
import org.springframework.samples.petclinic.scheduling.solver.CandidateSelectionService;

@Service
public class OfferService {

	private final AppointmentOfferRepository offers;

	private final AppointmentRepository appointments;

	private final CandidateSelectionService candidates;

	private final ReservationService reservations;

	private final FallbackQueueService fallback;

	private final SchedulingAuditService audit;

	private final ClinicSchedulingSettingsRepository settings;

	private final Clock clock;

	public OfferService(AppointmentOfferRepository offers, AppointmentRepository appointments,
			CandidateSelectionService candidates, ReservationService reservations, FallbackQueueService fallback,
			SchedulingAuditService audit, ClinicSchedulingSettingsRepository settings, Clock clock) {
		this.offers = offers;
		this.appointments = appointments;
		this.candidates = candidates;
		this.reservations = reservations;
		this.fallback = fallback;
		this.audit = audit;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional
	public Optional<AppointmentOffer> createOffer(RequestRevision revision, Authentication actor) {
		if (revision.getRequest().getState() != SchedulingRequestState.READY_FOR_SUGGESTION) {
			return Optional.empty();
		}
		return this.candidates.select(revision).flatMap(candidate -> {
			Instant now = this.clock.instant();
			AppointmentOffer offer = this.offers.save(new AppointmentOffer(revision, candidate.veterinarian(),
					candidate.startAt(), revision.getDurationMinutes(), now,
					now.plusSeconds(this.settings.findById(1).orElseThrow().getOfferHoldMinutes() * 60L),
					"Matches your request."));
			try {
				this.reservations.hold(offer);
				revision.getRequest().moveTo(SchedulingRequestState.OFFER_HELD);
				this.audit.record(actor, revision.getCorrelationId(), AuditAction.OFFER_HELD, "offer", offer.getId(),
						null, "HELD", null);
				return Optional.of(offer);
			}
			catch (DataIntegrityViolationException ex) {
				return Optional.empty();
			}
		});
	}

	@Transactional
	public Appointment accept(Integer offerId, Integer ownerId, Authentication actor) {
		AppointmentOffer offer = this.offers.findOwnedById(offerId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Offer is not available"));
		if (!offer.isActive(this.clock.instant())) {
			expire(offer, actor);
			throw new IllegalStateException("This offer is no longer available");
		}
		SchedulingRequest request = offer.getRevision().getRequest();
		Appointment appointment = this.appointments
			.save(new Appointment(request.getPet(), offer.getVet(), offer.getStartAt(), offer.getDurationMinutes(),
					AppointmentSource.OWNER_OFFER, request, offer.getRevision(), offer, null));
		offer.accept();
		this.reservations.promote(offer, appointment);
		request.moveTo(SchedulingRequestState.CONFIRMED);
		this.audit.record(actor, offer.getRevision().getCorrelationId(), AuditAction.OFFER_ACCEPTED, "offer", offerId,
				"HELD", "ACCEPTED", null);
		return appointment;
	}

	@Transactional
	public void reject(Integer offerId, Integer ownerId, String reason, Authentication actor) {
		AppointmentOffer offer = this.offers.findOwnedById(offerId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Offer is not available"));
		offer.reject(reason);
		this.reservations.releaseOffer(offer.getId());
		offer.getRevision().getRequest().moveTo(SchedulingRequestState.READY_FOR_SUGGESTION);
		this.audit.record(actor, offer.getRevision().getCorrelationId(), AuditAction.OFFER_REJECTED, "offer", offerId,
				"HELD", "REJECTED", reason);
		if (outcomeCount(offer.getRevision().getId()) >= 5) {
			this.fallback.route(offer.getRevision().getRequest());
		}
	}

	@Transactional
	public void releaseCurrent(RequestRevision revision, Authentication actor, String reason) {
		this.offers.findFirstByRevisionIdAndStateOrderByOfferedAtDesc(revision.getId(), OfferState.HELD)
			.ifPresent(offer -> {
				offer.release();
				this.reservations.releaseOffer(offer.getId());
				this.audit.record(actor, revision.getCorrelationId(), AuditAction.HOLD_RELEASED, "offer", offer.getId(),
						"HELD", "RELEASED", reason);
			});
	}

	@Transactional
	public void expireOutstanding(Authentication actor) {
		this.offers.findByStateAndExpiresAtBefore(OfferState.HELD, this.clock.instant())
			.forEach(offer -> expire(offer, actor));
	}

	private void expire(AppointmentOffer offer, Authentication actor) {
		if (offer.getState() != OfferState.HELD) {
			return;
		}
		offer.expire();
		this.reservations.releaseOffer(offer.getId());
		offer.getRevision().getRequest().moveTo(SchedulingRequestState.READY_FOR_SUGGESTION);
		this.audit.record(actor, offer.getRevision().getCorrelationId(), AuditAction.OFFER_EXPIRED, "offer",
				offer.getId(), "HELD", "EXPIRED", null);
		if (outcomeCount(offer.getRevision().getId()) >= 5) {
			this.fallback.route(offer.getRevision().getRequest());
		}
	}

	private long outcomeCount(Integer revisionId) {
		List<AppointmentOffer> offersForRevision = this.offers.findByRevisionId(revisionId);
		return offersForRevision.stream()
			.filter(offer -> offer.getState() == OfferState.REJECTED || offer.getState() == OfferState.EXPIRED)
			.count();
	}

}
