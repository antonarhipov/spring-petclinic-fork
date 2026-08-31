package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffQueueQueryService {

	private final QueueItemRepository queueItemRepository;

	private final ContactAttemptRepository contactAttemptRepository;

	private final OfferRepository offerRepository;

	private final OfferExclusionRepository offerExclusionRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final AccountRepository accountRepository;

	private final ProtectedPayloadService payloadService;

	public StaffQueueQueryService(QueueItemRepository queueItemRepository,
			ContactAttemptRepository contactAttemptRepository, OfferRepository offerRepository,
			OfferExclusionRepository offerExclusionRepository, OwnerRepository ownerRepository,
			VetRepository vetRepository, AccountRepository accountRepository, ProtectedPayloadService payloadService) {
		this.queueItemRepository = queueItemRepository;
		this.contactAttemptRepository = contactAttemptRepository;
		this.offerRepository = offerRepository;
		this.offerExclusionRepository = offerExclusionRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.accountRepository = accountRepository;
		this.payloadService = payloadService;
	}

	public List<QueueItemSummaryDto> getFilteredQueue(QueueState state, Long assigneeId, Urgency urgency,
			String fallbackReason) {
		List<QueueItem> items;
		if (state == null && assigneeId == null && urgency == null && fallbackReason == null) {
			items = this.queueItemRepository.findAllSortedByUrgencyAndAge();
		}
		else {
			items = this.queueItemRepository.findFiltered(state, assigneeId, urgency, fallbackReason);
		}

		Map<Long, String> accountsMap = this.accountRepository.findAll()
			.stream()
			.collect(Collectors.toMap(Account::getId, Account::getUsername, (a, b) -> a));

		return items.stream().map(item -> toSummaryDto(item, accountsMap)).toList();
	}

	public Optional<QueueItemDetailDto> getQueueItemDetail(Long queueItemId) {
		return this.queueItemRepository.findById(queueItemId).map(item -> {
			SchedulingRequest req = item.getRequest();
			Owner owner = this.ownerRepository.findById(req.getOwnerId()).orElse(null);
			Pet pet = owner != null ? owner.getPet(req.getPetId()) : null;

			String decryptedProse = "";
			Boolean consentGranted = null;
			TextRevision textRev = req.getCurrentTextRevision();
			if (textRev != null) {
				if (textRev.getConsentPayload() != null) {
					String consentJson = this.payloadService.decrypt(textRev.getConsentPayload(), String.class);
					consentGranted = consentJson.contains("\"consented\":true")
							|| consentJson.contains("\"consented\": true");
				}
				if (textRev.getProsePayload() != null) {
					decryptedProse = this.payloadService.decrypt(textRev.getProsePayload(), String.class);
				}
			}

			WorkflowRevision workflowRev = item.getWorkflowRevision() != null ? item.getWorkflowRevision()
					: req.getCurrentWorkflowRevision();
			String decryptedReason = "";
			Integer duration = null;
			Integer preferredVetId = null;
			String preferredVetName = null;
			Integer requiredSpecialtyId = null;
			String requiredSpecialtyName = null;
			List<AvailabilityWindow> windows = List.of();

			if (workflowRev != null) {
				if (workflowRev.getReasonPayload() != null) {
					decryptedReason = this.payloadService.decrypt(workflowRev.getReasonPayload(), String.class);
				}
				duration = workflowRev.getDurationMinutes();
				preferredVetId = workflowRev.getPreferredVetId();
				if (preferredVetId != null) {
					preferredVetName = this.vetRepository.findById(preferredVetId)
						.map(v -> "Dr. " + v.getFirstName() + " " + v.getLastName())
						.orElse(null);
				}
				requiredSpecialtyId = workflowRev.getRequiredSpecialtyId();
				if (requiredSpecialtyId != null) {
					requiredSpecialtyName = this.vetRepository.findSpecialties()
						.stream()
						.filter(s -> s.getId().equals(workflowRev.getRequiredSpecialtyId()))
						.map(Specialty::getName)
						.findFirst()
						.orElse(null);
				}
				windows = List.copyOf(workflowRev.getAvailabilityWindows());
			}

			String assigneeUsername = null;
			if (item.getAssigneeAccountId() != null) {
				assigneeUsername = this.accountRepository.findById(item.getAssigneeAccountId())
					.map(Account::getUsername)
					.orElse(null);
			}

			List<ContactAttempt> attempts = this.contactAttemptRepository
				.findByQueueItemIdOrderByAttemptedAtAsc(item.getId());
			List<ContactAttemptDto> attemptDtos = attempts.stream().map(a -> {
				String actorUser = this.accountRepository.findById(a.getActorAccountId())
					.map(Account::getUsername)
					.orElse("Staff");
				String note = "";
				if (a.getNotePayload() != null) {
					note = this.payloadService.decrypt(a.getNotePayload(), String.class);
				}
				return new ContactAttemptDto(a.getId(), a.getActorAccountId(), actorUser, a.getAttemptedAt(),
						a.getOutcome(), note);
			}).toList();

			List<Offer> activeOffers = this.offerRepository.findByRequestIdOrderByStartAtAsc(req.getId())
				.stream()
				.filter(o -> o.getState() == OfferState.HELD)
				.toList();

			List<OfferExclusion> exclusions = workflowRev != null
					? this.offerExclusionRepository.findByWorkflowRevisionId(workflowRev.getId()) : List.of();

			return new QueueItemDetailDto(item.getId(), req.getId(), req.getPetId(), pet != null ? pet.getName() : "",
					pet != null && pet.getType() != null ? pet.getType().getName() : "", req.getOwnerId(),
					owner != null ? owner.getFirstName() + " " + owner.getLastName() : "",
					owner != null ? owner.getTelephone() : "", owner != null ? owner.getAddress() : "",
					owner != null ? owner.getCity() : "", item.getUrgency(), item.getFallbackReason(), item.getState(),
					item.getAwaitingReason(), item.getAssigneeAccountId(), assigneeUsername, item.getCreatedAt(),
					item.getUpdatedAt(), decryptedProse, consentGranted,
					workflowRev != null ? workflowRev.getId() : null, decryptedReason, duration, preferredVetId,
					preferredVetName, requiredSpecialtyId, requiredSpecialtyName, windows, attemptDtos, activeOffers,
					exclusions);
		});
	}

	private QueueItemSummaryDto toSummaryDto(QueueItem item, Map<Long, String> accountsMap) {
		SchedulingRequest req = item.getRequest();
		Owner owner = this.ownerRepository.findById(req.getOwnerId()).orElse(null);
		Pet pet = owner != null ? owner.getPet(req.getPetId()) : null;
		String assigneeUsername = item.getAssigneeAccountId() != null ? accountsMap.get(item.getAssigneeAccountId())
				: null;

		return new QueueItemSummaryDto(item.getId(), req.getId(), req.getPetId(), pet != null ? pet.getName() : "",
				req.getOwnerId(), owner != null ? owner.getFirstName() + " " + owner.getLastName() : "",
				owner != null ? owner.getTelephone() : "", item.getUrgency(), item.getFallbackReason(), item.getState(),
				item.getAwaitingReason(), item.getAssigneeAccountId(), assigneeUsername, item.getCreatedAt(),
				item.getLastContactAt());
	}

	public record QueueItemSummaryDto(Long id, Long requestId, Integer petId, String petName, Integer ownerId,
			String ownerName, String ownerTelephone, Urgency urgency, String fallbackReason, QueueState state,
			AwaitingReason awaitingReason, Long assigneeAccountId, String assigneeUsername, Instant createdAt,
			Instant lastContactAt) {
	}

	public record ContactAttemptDto(Long id, Long actorAccountId, String actorUsername, Instant attemptedAt,
			ContactOutcome outcome, String note) {
	}

	public record QueueItemDetailDto(Long id, Long requestId, Integer petId, String petName, String petType,
			Integer ownerId, String ownerName, String ownerTelephone, String ownerAddress, String ownerCity,
			Urgency urgency, String fallbackReason, QueueState state, AwaitingReason awaitingReason,
			Long assigneeAccountId, String assigneeUsername, Instant createdAt, Instant updatedAt, String originalProse,
			Boolean consentGranted, Long workflowRevisionId, String visitReason, Integer durationMinutes,
			Integer preferredVetId, String preferredVetName, Integer requiredSpecialtyId, String requiredSpecialtyName,
			List<AvailabilityWindow> windows, List<ContactAttemptDto> contactAttempts, List<Offer> activeOffers,
			List<OfferExclusion> exclusions) {
	}

}
