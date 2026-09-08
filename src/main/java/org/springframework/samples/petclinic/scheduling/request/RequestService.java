package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

@Service
public class RequestService {

	private final SchedulingRequestRepository requests;

	private final EntityManager entityManager;

	private final Clock clock;

	private final Optional<InterpretationLauncher> interpretationLauncher;

	private final Optional<SlotSuggestionPort> slotSuggestions;

	private final TransactionTemplate writeTransaction;

	private final TransactionTemplate readTransaction;

	private final InterpretationRepository interpretations;

	public RequestService(SchedulingRequestRepository requests, InterpretationRepository interpretations,
			EntityManager entityManager, Clock clock, PlatformTransactionManager transactionManager,
			Optional<InterpretationLauncher> interpretationLauncher, Optional<SlotSuggestionPort> slotSuggestions) {
		this.requests = requests;
		this.interpretations = interpretations;
		this.entityManager = entityManager;
		this.clock = clock;
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
		this.interpretationLauncher = interpretationLauncher;
		this.slotSuggestions = slotSuggestions;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public SchedulingRequest createForOwner(int petId, String requestText) {
		CreationResult result = startForOwner(petId, requestText);
		if (result.request() == null) {
			throw new IllegalArgumentException(result.messageKey());
		}
		return result.request();
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CreationResult startForOwner(int petId, String requestText) {
		String validationKey = validateText(requestText);
		if (validationKey != null) {
			return CreationResult.refused(validationKey);
		}
		try {
			return CreationResult.created(persist(petId, requestText, false));
		}
		catch (DataIntegrityViolationException ex) {
			SchedulingRequest winner = this.readTransaction.execute(status -> this.requests.findByActivePetId(petId)
				.orElseThrow(() -> new IllegalStateException("Active request constraint failed without a winner", ex)));
			return CreationResult.existing(winner);
		}
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CreationResult createForStaff(int petId, String requestText) {
		String validationKey = validateText(requestText);
		if (validationKey != null) {
			return CreationResult.refused(validationKey);
		}
		try {
			return CreationResult.created(persist(petId, requestText, true));
		}
		catch (DataIntegrityViolationException ex) {
			SchedulingRequest winner = this.readTransaction.execute(status -> this.requests.findByActivePetId(petId)
				.orElseThrow(() -> new IllegalStateException("Active request constraint failed without a winner", ex)));
			return CreationResult.existing(winner);
		}
	}

	@Transactional
	public SchedulingRequest consent(int requestId) {
		SchedulingRequest request = mutate(requestId, "CONSENT", RequestState.AWAITING_CONSENT,
				requestToChange -> requestToChange.transitionTo(RequestState.INTERPRETING, today(), now()));
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					launcher().launch(requestId);
				}
			});
		}
		else {
			launcher().launch(requestId);
		}
		return request;
	}

	@Transactional
	public SchedulingRequest decline(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			requireState(request, "DECLINE", RequestState.AWAITING_CONSENT);
			request.routeToStaff(WithStaffReason.DECLINED_CONSENT, null, today(), now());
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest editText(int requestId, String requestText) {
		String validationKey = validateText(requestText);
		if (validationKey != null) {
			throw new IllegalArgumentException(validationKey);
		}
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			requireState(request, "EDIT_TEXT", RequestState.AWAITING_CONSENT, RequestState.INTERPRETATION_FAILED,
					RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED);
			if (request.getState() == RequestState.SUGGESTION_OFFERED) {
				if (this.slotSuggestions.isPresent()) {
					this.slotSuggestions.get().releaseSuggestion(request);
				}
				this.entityManager
					.createQuery("delete from Appointment a where a.request.id = :requestId and a.status = :status")
					.setParameter("requestId", requestId)
					.setParameter("status", AppointmentStatus.HELD)
					.executeUpdate();
			}
			request.replaceText(requestText, today(), now());
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest interpretationSucceeded(int requestId, Interpretation interpretation) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			if (request.getState() != RequestState.INTERPRETING) {
				return request;
			}
			requireOrigin(interpretation, InterpretationOrigin.AI, "system interpretation");
			interpretation.attachTo(request);
			Interpretation saved = this.interpretations.save(interpretation);
			request.addInterpretation(saved, today(), now());
			request.transitionTo(RequestState.INTERPRETED, today(), now());
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest interpretationFailed(int requestId, InterpretationFailure failure) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			if (request.getState() != RequestState.INTERPRETING) {
				return request;
			}
			failure.attachTo(request);
			request.addFailure(failure, today(), now());
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest aiUnavailable(int requestId) {
		return mutate(requestId, "AI_UNAVAILABLE", RequestState.INTERPRETING,
				request -> request.routeToStaff(WithStaffReason.AI_UNAVAILABLE, null, today(), now()));
	}

	@Transactional
	public SchedulingRequest routeToStaff(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			requireState(request, "ROUTE_TO_STAFF", RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED,
					RequestState.SUGGESTION_OFFERED);
			if (request.getState() == RequestState.SUGGESTION_OFFERED) {
				if (this.slotSuggestions.isPresent()) {
					this.slotSuggestions.get().releaseSuggestion(request);
				}
				this.entityManager
					.createQuery("delete from Appointment a where a.request.id = :requestId and a.status = :status")
					.setParameter("requestId", requestId)
					.setParameter("status", AppointmentStatus.HELD)
					.executeUpdate();
			}
			request.routeToStaff(WithStaffReason.OWNER_CHOICE, null, today(), now());
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest confirmInterpretation(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			requireState(request, "CONFIRM", RequestState.INTERPRETED);
			if (request.getCurrentInterpretation() != null && request.getCurrentInterpretation().isOtherSpecialty()) {
				request.routeToStaff(WithStaffReason.UNMATCHED_SPECIALTY, null, today(), now());
			}
			else if (this.slotSuggestions.isPresent() && this.slotSuggestions.get().placeSuggestion(request)) {
				request.transitionTo(RequestState.SUGGESTION_OFFERED, today(), now());
			}
			else {
				request.routeToStaff(WithStaffReason.NO_SLOTS, null, today(), now());
			}
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest acceptSuggestion(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = requireInState(requestId, "ACCEPT", RequestState.SUGGESTION_OFFERED);
			SlotSuggestionPort.SuggestionAcceptance result = slots().acceptSuggestion(request);
			if (result == SlotSuggestionPort.SuggestionAcceptance.CONFIRMED) {
				request.close(RequestState.ACCEPTED, today(), now());
			}
			else if (result == SlotSuggestionPort.SuggestionAcceptance.EXHAUSTED) {
				request.routeToStaff(WithStaffReason.NO_SLOTS, null, today(), now());
			}
			return this.requests.save(request);
		});
	}

	@Transactional
	public SchedulingRequest anotherSuggestion(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = requireInState(requestId, "ANOTHER_OPTION", RequestState.SUGGESTION_OFFERED);
			if (this.slotSuggestions.isEmpty() || !this.slotSuggestions.get().replaceSuggestion(request)) {
				if (this.slotSuggestions.isPresent()) {
					this.slotSuggestions.get().releaseSuggestion(request);
				}
				this.entityManager
					.createQuery("delete from Appointment a where a.request.id = :requestId and a.status = :status")
					.setParameter("requestId", requestId)
					.setParameter("status", AppointmentStatus.HELD)
					.executeUpdate();
				request.routeToStaff(WithStaffReason.NO_SLOTS, null, today(), now());
			}
			return this.requests.save(request);
		});
	}

	@Transactional
	public ActionResult releaseHold(int requestId, long expectedVersion, String staffReason) {
		if (staffReason == null || staffReason.isBlank()) {
			return ActionResult.refused(require(requestId), "scheduling.action.requiredReason");
		}
		return staffAction(requestId, expectedVersion, "RELEASE_HOLD", RequestState.SUGGESTION_OFFERED, request -> {
			slots().releaseSuggestion(request);
			request.routeToStaff(WithStaffReason.HOLD_RELEASED, staffReason, today(), now());
		});
	}

	@Transactional
	public SchedulingRequest scheduleChanged(int requestId) {
		return mutate(requestId, "SCHEDULE_CHANGED", RequestState.SUGGESTION_OFFERED, request -> {
			slots().releaseSuggestion(request);
			request.routeToStaff(WithStaffReason.SCHEDULE_CHANGED, null, today(), now());
		});
	}

	@Transactional
	public ActionResult authorInterpretation(int requestId, long expectedVersion,
			StaffInterpretationForm.Values values) {
		if (values.careType() == CareType.SPECIALTY && !"OTHER".equals(values.specialty())) {
			Long count = this.entityManager
				.createQuery("select count(specialty) from Specialty specialty where specialty.name = :name",
						Long.class)
				.setParameter("name", values.specialty())
				.getSingleResult();
			if (count == 0) {
				return ActionResult.refused(require(requestId), "scheduling.staff.interpretation.specialty.invalid");
			}
		}
		org.springframework.samples.petclinic.vet.Vet preferredVet = null;
		if (values.preferredVetId() != null) {
			preferredVet = this.entityManager.find(org.springframework.samples.petclinic.vet.Vet.class,
					values.preferredVetId());
			if (preferredVet == null) {
				return ActionResult.refused(require(requestId), "scheduling.staff.interpretation.vet.invalid");
			}
		}
		Interpretation interpretation = new Interpretation(true, values.careType(), values.specialty(),
				values.specialtyLabel(), values.durationMinutes(), preferredVet, InterpretationOrigin.STAFF, null, null,
				null, today(), now());
		for (StaffInterpretationForm.WindowValue window : values.preferredWindows()) {
			interpretation.addWindow(toWindow(window));
		}
		for (StaffInterpretationForm.WindowValue window : values.allowedWindows()) {
			interpretation.addWindow(toWindow(window));
		}
		for (StaffInterpretationForm.WindowValue window : values.excludedWindows()) {
			interpretation.addWindow(toWindow(window));
		}
		return authorInterpretation(requestId, expectedVersion, interpretation);
	}

	private InterpretationWindow toWindow(StaffInterpretationForm.WindowValue window) {
		return new InterpretationWindow(window.kind(), window.weekday(), window.date(), window.startTime(),
				window.endTime());
	}

	@Transactional
	public ActionResult authorInterpretation(int requestId, long expectedVersion, Interpretation interpretation) {
		return staffAction(requestId, expectedVersion, "AUTHOR_INTERPRETATION", RequestState.WITH_STAFF, request -> {
			requireOrigin(interpretation, InterpretationOrigin.STAFF, "staff interpretation");
			if (sameStructuredValues(request.getCurrentInterpretation(), interpretation)) {
				return;
			}
			interpretation.attachTo(request);
			Interpretation saved = this.interpretations.save(interpretation);
			request.addInterpretation(saved, today(), now());
		});
	}

	private boolean sameStructuredValues(Interpretation current, Interpretation submitted) {
		if (current == null || current.isUnderstood() != submitted.isUnderstood()
				|| current.getCareType() != submitted.getCareType()
				|| !java.util.Objects.equals(current.getSpecialty(), submitted.getSpecialty())
				|| !java.util.Objects.equals(current.getSpecialtyLabel(), submitted.getSpecialtyLabel())
				|| !java.util.Objects.equals(current.getDurationMinutes(), submitted.getDurationMinutes())
				|| !java.util.Objects.equals(id(current.getPreferredVet()), id(submitted.getPreferredVet()))
				|| current.getWindows().size() != submitted.getWindows().size()) {
			return false;
		}
		for (int index = 0; index < current.getWindows().size(); index++) {
			InterpretationWindow left = current.getWindows().get(index);
			InterpretationWindow right = submitted.getWindows().get(index);
			if (!java.util.Objects.equals(left.getWindowKind(), right.getWindowKind())
					|| !java.util.Objects.equals(left.getWeekday(), right.getWeekday())
					|| !java.util.Objects.equals(left.getDate(), right.getDate())
					|| !java.util.Objects.equals(left.getStartTime(), right.getStartTime())
					|| !java.util.Objects.equals(left.getEndTime(), right.getEndTime())) {
				return false;
			}
		}
		return true;
	}

	private Integer id(org.springframework.samples.petclinic.vet.Vet vet) {
		return vet != null ? vet.getId() : null;
	}

	@Transactional
	public ActionResult placeStaffSuggestion(int requestId, long expectedVersion,
			SlotSuggestionPort.StaffSuggestionCommand command, String reason, String changedBy) {
		if (reason == null || reason.isBlank() || changedBy == null || changedBy.isBlank()) {
			return ActionResult.refused(require(requestId), "scheduling.action.requiredReason");
		}
		return staffAction(requestId, expectedVersion, "STAFF_SUGGEST", RequestState.WITH_STAFF, request -> {
			if (!slots().placeStaffSuggestion(request, command, reason, changedBy)) {
				throw new IllegalStateException("Slot suggestion port returned no candidate for a staff-selected slot");
			}
			request.transitionTo(RequestState.SUGGESTION_OFFERED, today(), now());
		});
	}

	@Transactional
	public ActionResult bookDirectly(int requestId, long expectedVersion,
			SlotSuggestionPort.StaffDirectBookingCommand command) {
		return staffAction(requestId, expectedVersion, "STAFF_BOOK", RequestState.WITH_STAFF, request -> {
			if (!slots().bookDirectly(request, command)) {
				throw new IllegalStateException("Slot suggestion port refused the staff-selected booking");
			}
			request.close(RequestState.ACCEPTED, today(), now());
		});
	}

	@Transactional
	public SchedulingRequest abandon(int requestId) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			if (request.getState() == RequestState.ACCEPTED || request.getState() == RequestState.ABANDONED) {
				throw new IllegalRequestTransitionException(request.getState(), "ABANDON");
			}
			if (request.getState() == RequestState.SUGGESTION_OFFERED) {
				if (this.slotSuggestions.isPresent()) {
					this.slotSuggestions.get().releaseSuggestion(request);
				}
				this.entityManager
					.createQuery("delete from Appointment a where a.request.id = :requestId and a.status = :status")
					.setParameter("requestId", requestId)
					.setParameter("status", AppointmentStatus.HELD)
					.executeUpdate();
			}
			request.close(RequestState.ABANDONED, today(), now());
			return this.requests.save(request);
		});
	}

	private SchedulingRequest persist(int petId, String requestText, boolean staffCreated) {
		return this.writeTransaction.execute(status -> {
			Pet pet = this.entityManager.getReference(Pet.class, petId);
			SchedulingRequest request = staffCreated ? SchedulingRequest.withStaff(pet, requestText, today(), now())
					: SchedulingRequest.awaitingConsent(pet, requestText, today(), now());
			return this.requests.saveAndFlush(request);
		});
	}

	private SchedulingRequest mutate(int requestId, String action, RequestState requiredState,
			java.util.function.Consumer<SchedulingRequest> mutation) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = requireInState(requestId, action, requiredState);
			mutation.accept(request);
			return this.requests.save(request);
		});
	}

	private ActionResult staffAction(int requestId, long expectedVersion, String action, RequestState requiredState,
			java.util.function.Consumer<SchedulingRequest> mutation) {
		return this.writeTransaction.execute(status -> {
			SchedulingRequest request = require(requestId);
			if (request.getVersion() != expectedVersion) {
				return ActionResult.refused(request, "scheduling.request.stale");
			}
			requireState(request, action, requiredState);
			if (this.requests.claimStaffAction(requestId, expectedVersion, requiredState) != 1) {
				this.entityManager.clear();
				return ActionResult.refused(require(requestId), "scheduling.request.stale");
			}
			this.entityManager.clear();
			request = require(requestId);
			mutation.accept(request);
			return ActionResult.completed(this.requests.save(request));
		});
	}

	private SchedulingRequest require(int requestId) {
		return this.requests.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown scheduling request " + requestId));
	}

	private SchedulingRequest requireInState(int requestId, String action, RequestState state) {
		SchedulingRequest request = require(requestId);
		requireState(request, action, state);
		return request;
	}

	private void requireState(SchedulingRequest request, String action, RequestState... allowed) {
		for (RequestState state : allowed) {
			if (request.getState() == state) {
				return;
			}
		}
		throw new IllegalRequestTransitionException(request.getState(), action);
	}

	private InterpretationLauncher launcher() {
		return this.interpretationLauncher
			.orElseThrow(() -> new IllegalStateException("No InterpretationLauncher configured"));
	}

	private SlotSuggestionPort slots() {
		return this.slotSuggestions.orElseThrow(() -> new IllegalStateException("No SlotSuggestionPort configured"));
	}

	private void requireOrigin(Interpretation interpretation, InterpretationOrigin expected, String action) {
		if (interpretation.getOrigin() != expected) {
			throw new IllegalArgumentException(action + " requires origin " + expected);
		}
	}

	private LocalDate today() {
		return LocalDate.now(this.clock);
	}

	private LocalTime now() {
		return LocalTime.now(this.clock);
	}

	private String validateText(String requestText) {
		if (requestText == null || requestText.isBlank()) {
			return "scheduling.request.text.required";
		}
		return requestText.length() > 2000 ? "scheduling.request.text.size" : null;
	}

	public record CreationResult(SchedulingRequest request, String messageKey, boolean created) {

		static CreationResult created(SchedulingRequest request) {
			return new CreationResult(request, null, true);
		}

		static CreationResult existing(SchedulingRequest request) {
			return new CreationResult(request, null, false);
		}

		static CreationResult refused(String messageKey) {
			return new CreationResult(null, messageKey, false);
		}
	}

	public record ActionResult(SchedulingRequest request, String messageKey) {

		static ActionResult completed(SchedulingRequest request) {
			return new ActionResult(request, null);
		}

		static ActionResult refused(SchedulingRequest request, String messageKey) {
			return new ActionResult(request, messageKey);
		}
	}

}
