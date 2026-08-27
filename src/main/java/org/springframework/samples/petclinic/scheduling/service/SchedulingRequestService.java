package org.springframework.samples.petclinic.scheduling.service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto;
import org.springframework.samples.petclinic.scheduling.exception.RateLimitExceededException;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CalendarExpressionType;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.OperationType;
import org.springframework.samples.petclinic.scheduling.model.OrdinalWeek;
import org.springframework.samples.petclinic.scheduling.model.Rejection;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingRequestService {

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final OperationTokenRepository operationTokenRepository;

	private final ReservationRepository reservationRepository;

	private final RejectionRepository rejectionRepository;

	private final StaffClaimRepository staffClaimRepository;

	private final RequestAvailabilityWindowRepository availabilityWindowRepository;

	private final RequestAvailabilityIntervalRepository availabilityIntervalRepository;

	private final PetRepository petRepository;

	private final org.springframework.samples.petclinic.owner.OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final AuditService auditService;

	private final LockCoordinator lockCoordinator;

	private final SchedulingTimeService timeService;

	private final AvailabilityWindowResolver availabilityWindowResolver;

	public SchedulingRequestService(SchedulingRequestRepository schedulingRequestRepository,
			OperationTokenRepository operationTokenRepository, ReservationRepository reservationRepository,
			RejectionRepository rejectionRepository, StaffClaimRepository staffClaimRepository,
			RequestAvailabilityWindowRepository availabilityWindowRepository,
			RequestAvailabilityIntervalRepository availabilityIntervalRepository, PetRepository petRepository,
			org.springframework.samples.petclinic.owner.OwnerRepository ownerRepository, VetRepository vetRepository,
			SpecialtyRepository specialtyRepository, ClinicSettingsRepository clinicSettingsRepository,
			AuditService auditService, LockCoordinator lockCoordinator, SchedulingTimeService timeService,
			AvailabilityWindowResolver availabilityWindowResolver) {
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.operationTokenRepository = operationTokenRepository;
		this.reservationRepository = reservationRepository;
		this.rejectionRepository = rejectionRepository;
		this.staffClaimRepository = staffClaimRepository;
		this.availabilityWindowRepository = availabilityWindowRepository;
		this.availabilityIntervalRepository = availabilityIntervalRepository;
		this.petRepository = petRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.auditService = auditService;
		this.lockCoordinator = lockCoordinator;
		this.timeService = timeService;
		this.availabilityWindowResolver = availabilityWindowResolver;
	}

	@Transactional
	public SchedulingRequest createRequest(Integer ownerId, Integer petId, String originalText, String language,
			String username) {
		Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Pet not found or does not belong to owner"));

		Owner owner = ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		lockCoordinator.lockPet(petId);

		List<SchedulingRequest> activeRequests = schedulingRequestRepository.findByPetIdAndStatusNotIn(petId,
				List.of(RequestStatus.CANCELLED, RequestStatus.CONFIRMED, RequestStatus.EXPIRED));
		if (!activeRequests.isEmpty()) {
			return activeRequests.get(0);
		}

		if (originalText == null) {
			throw new IllegalArgumentException("Original text must not be null");
		}
		String normalized = Normalizer.normalize(originalText, Normalizer.Form.NFC);
		normalized = normalized.replace("\r\n", "\n").replace("\r", "\n").trim();
		int codePoints = normalized.codePointCount(0, normalized.length());
		if (codePoints < 1 || codePoints > 2000) {
			throw new IllegalArgumentException("Original text must be between 1 and 2000 code points");
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);

		Instant now = timeService.now();
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setOriginalText(normalized);
		String lang = (language != null && !language.trim().isEmpty()) ? language.trim().toLowerCase() : "en";
		request.setLanguage(lang);
		request.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));

		if (!"en".equals(lang)) {
			request.setStatus(RequestStatus.STAFF_QUEUED);
			request.setLastClarificationReason("UNSUPPORTED_LANGUAGE");
			request.setFirstQueuedAt(now);
			request.setFallbackDeadline(now.plus(Duration.ofDays(settings.getFallbackDeadlineDays())));
		}
		else {
			request.setStatus(RequestStatus.AWAITING_CONSENT);
		}

		SchedulingRequest saved = schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_SUBMITTED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(saved.getId()), "REQUEST_SUBMITTED",
				Map.of("language", lang, "petId", String.valueOf(petId)));

		return saved;
	}

	@Transactional
	public String recordConsentAndDispatchAI(Integer requestId, Integer ownerId, boolean consent, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Scheduling request not found");
		}
		if (!request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request does not belong to owner");
		}
		if (request.getStatus() != RequestStatus.AWAITING_CONSENT) {
			throw new IllegalStateException("Request is not awaiting consent");
		}

		Instant now = timeService.now();
		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);

		if (!consent) {
			request.setStatus(RequestStatus.STAFF_QUEUED);
			request.setLastClarificationReason("CONSENT_DECLINED");
			request.setFirstQueuedAt(now);
			request.setFallbackDeadline(now.plus(Duration.ofDays(settings.getFallbackDeadlineDays())));
			schedulingRequestRepository.save(request);

			auditService.recordEvent(AuditEventType.CONSENT_GRANTED, username != null ? username : "owner", "OWNER",
					"SCHEDULING_REQUEST", String.valueOf(requestId), "CONSENT_DECLINED", Map.of("consent", "false"));
			return null;
		}

		// Rate limit check: 10 dispatches in rolling hour
		Instant oneHourAgo = now.minus(Duration.ofHours(1));
		long dispatchCount = operationTokenRepository.countByRequestOwnerIdAndOperationTypeAndDispatchedAtAfter(ownerId,
				OperationType.AI_INTERPRETATION, oneHourAgo);

		if (dispatchCount >= 10) {
			List<OperationToken> pastTokens = operationTokenRepository
				.findByRequestOwnerIdAndOperationTypeAndDispatchedAtAfterOrderByDispatchedAtAsc(ownerId,
						OperationType.AI_INTERPRETATION, oneHourAgo);
			Instant oldest = pastTokens.isEmpty() ? now : pastTokens.get(0).getDispatchedAt();
			Instant retryAt = oldest.plus(Duration.ofHours(1));
			throw new RateLimitExceededException(retryAt);
		}

		String token = UUID.randomUUID().toString();
		OperationToken op = new OperationToken();
		op.setToken(token);
		op.setRequest(request);
		op.setOperationType(OperationType.AI_INTERPRETATION);
		op.setStatus(OperationStatus.DISPATCHED);
		op.setDispatchedAt(now);
		op.setDeadline(now.plus(Duration.ofSeconds(60)));
		operationTokenRepository.save(op);

		request.setStatus(RequestStatus.INTERPRETING);
		request.setConsentGivenAt(now);
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.CONSENT_GRANTED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "CONSENT_GRANTED", Map.of("consent", "true"));
		auditService.recordEvent(AuditEventType.OPERATION_DISPATCHED, username != null ? username : "owner", "OWNER",
				"OPERATION", token, "AI_INTERPRETATION", Map.of("requestId", String.valueOf(requestId)));

		return token;
	}

	@Transactional
	public void processAIInterpretationSuccess(String token, Integer requestId,
			AIInterpretationResponseDto responseDto) {
		Optional<OperationToken> opOpt = operationTokenRepository.findByToken(token);
		if (opOpt.isEmpty()) {
			return;
		}
		OperationToken op = opOpt.get();
		if (op.getStatus() != OperationStatus.DISPATCHED) {
			return;
		}

		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || request.getStatus() != RequestStatus.INTERPRETING) {
			return;
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		Instant now = timeService.now();

		if (op.getDeadline() != null && op.getDeadline().isBefore(now)) {
			op.setStatus(OperationStatus.TIMED_OUT);
			operationTokenRepository.save(op);
			fallbackToStaff(request, "AI_UNAVAILABLE", now, settings);
			auditService.recordEvent(AuditEventType.OPERATION_FAILED, "ai-service", "SYSTEM", "OPERATION", token,
					"AI_UNAVAILABLE", Map.of("reason", "DEADLINE_EXPIRED"));
			return;
		}

		op.setStatus(OperationStatus.COMPLETED);
		operationTokenRepository.save(op);

		// 1. Unsafe / Contradictory / Invalid check
		if (responseDto.issues() != null) {
			for (String issue : responseDto.issues()) {
				if ("UNSAFE_CONTENT".equals(issue) || "CONTRADICTORY_CLINICAL_ROUTING".equals(issue)
						|| "INVALID_AI_OUTPUT".equals(issue)) {
					fallbackToStaff(request, "INVALID_AI_OUTPUT", now, settings);
					return;
				}
			}
		}

		// 2. Specialty validation
		if ("SPECIALTY".equalsIgnoreCase(responseDto.careType())) {
			request.setCareType(CareType.SPECIALTY);
			if (responseDto.specialtyName() != null) {
				Optional<Specialty> specOpt = specialtyRepository.findAll()
					.stream()
					.filter(s -> s.isActive() && s.getName().equalsIgnoreCase(responseDto.specialtyName()))
					.findFirst();
				if (specOpt.isPresent()) {
					request.setRequiredSpecialty(specOpt.get());
				}
				else {
					fallbackToStaff(request, "INVALID_AI_OUTPUT", now, settings);
					return;
				}
			}
			else {
				fallbackToStaff(request, "INVALID_AI_OUTPUT", now, settings);
				return;
			}
		}
		else {
			request.setCareType(CareType.GENERAL);
			request.setRequiredSpecialty(null);
		}

		// 3. Preferred vet validation
		if (responseDto.preferredVetName() != null && !responseDto.preferredVetName().trim().isEmpty()) {
			Optional<Vet> vetOpt = vetRepository.findAll()
				.stream()
				.filter(v -> v.isActive() && (v.getFirstName() + " " + v.getLastName()).toLowerCase()
					.contains(responseDto.preferredVetName().toLowerCase()))
				.findFirst();
			if (vetOpt.isPresent()) {
				request.setPreferredVet(vetOpt.get());
			}
			else {
				request.setStatus(RequestStatus.CLARIFICATION_REQUIRED);
				request.setLastClarificationReason("UNKNOWN_PREFERRED_VET");
				request.setClarificationCount(request.getClarificationCount() + 1);
				schedulingRequestRepository.save(request);
				return;
			}
		}

		// 4. Duration normalization
		int duration = responseDto.durationMinutes() != null ? responseDto.durationMinutes()
				: settings.getDefaultDurationMinutes();
		if (duration < settings.getMinDurationMinutes()) {
			duration = settings.getMinDurationMinutes();
		}
		if (duration > settings.getMaxDurationMinutes()) {
			duration = settings.getMaxDurationMinutes();
		}
		duration = ((duration + 14) / 15) * 15;
		request.setDurationMinutes(duration);

		if (!replaceSymbolicWindows(request, responseDto, settings)) {
			fallbackToStaff(request, "INVALID_AI_OUTPUT", now, settings);
			return;
		}

		// 5. Urgency check
		if (responseDto.urgent()) {
			request.setUrgent(true);
			fallbackToStaff(request, "URGENCY", now, settings);
			return;
		}

		// 6. Clarification check
		if (responseDto.issues() != null && responseDto.issues().contains("INCOMPLETE_AVAILABILITY")) {
			request.setStatus(RequestStatus.CLARIFICATION_REQUIRED);
			request.setLastClarificationReason("INCOMPLETE_AVAILABILITY");
			request.setClarificationCount(request.getClarificationCount() + 1);
			schedulingRequestRepository.save(request);
			return;
		}

		// 7. Normal success -> Awaiting confirmation
		request.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		request.setAiSummary(responseDto.summary());
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_INTERPRETED, "ai-service", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "AI_SUCCESS",
				Map.of("careType", request.getCareType().name(), "duration", String.valueOf(duration)));
	}

	private boolean replaceSymbolicWindows(SchedulingRequest request, AIInterpretationResponseDto response,
			ClinicSettings settings) {
		List<RequestAvailabilityWindow> normalized = new java.util.ArrayList<>();
		try {
			List<AIInterpretationResponseDto.TimeWindowDto> windows = response.windows() != null ? response.windows()
					: List.of();
			for (int i = 0; i < windows.size(); i++) {
				normalized.add(toAvailabilityWindow(request, i, windows.get(i), settings));
			}
		}
		catch (RuntimeException ex) {
			return false;
		}
		availabilityIntervalRepository.deleteByRequestId(request.getId());
		availabilityWindowRepository.deleteByRequestId(request.getId());
		availabilityWindowRepository.saveAll(normalized);
		return true;
	}

	private RequestAvailabilityWindow toAvailabilityWindow(SchedulingRequest request, int order,
			AIInterpretationResponseDto.TimeWindowDto dto, ClinicSettings settings) {
		AvailabilityWindowKind kind = AvailabilityWindowKind.valueOf(normalizeEnum(dto.kind()));
		CalendarExpressionType expression = CalendarExpressionType.valueOf(normalizeEnum(dto.dateExpression()));
		validateCalendarFields(dto, expression);

		RequestAvailabilityWindow window = new RequestAvailabilityWindow();
		window.setRequest(request);
		window.setWindowOrder(order);
		window.setKind(kind);
		window.setExpressionType(expression);
		if (dto.explicitDate() != null) {
			window.setExplicitDate(LocalDate.parse(dto.explicitDate()));
		}
		if (dto.dayOfWeek() != null) {
			window.setDayOfWeek(DayOfWeek.valueOf(normalizeEnum(dto.dayOfWeek())).getValue());
		}
		window.setWeekOffset(dto.weekOffset());
		window.setMonthOffset(dto.monthOffset());
		if (dto.month() != null) {
			window.setMonthOfYear(Month.valueOf(normalizeEnum(dto.month())).getValue());
		}
		if (dto.ordinalWeek() != null) {
			window.setOrdinalWeek(OrdinalWeek.valueOf(normalizeEnum(dto.ordinalWeek())));
		}
		applyTimeBounds(window, dto, settings);
		return window;
	}

	private void validateCalendarFields(AIInterpretationResponseDto.TimeWindowDto dto,
			CalendarExpressionType expression) {
		if (dto.weekOffset() != null && dto.weekOffset() < 0 || dto.monthOffset() != null && dto.monthOffset() < 0) {
			throw new IllegalArgumentException("Calendar offsets must be non-negative");
		}
		boolean hasExplicitDate = dto.explicitDate() != null;
		boolean hasWeekOffset = dto.weekOffset() != null;
		boolean hasMonthOffset = dto.monthOffset() != null;
		boolean hasMonth = dto.month() != null;
		switch (expression) {
			case ANY_DATE -> require(
					!hasExplicitDate && !hasWeekOffset && !hasMonthOffset && !hasMonth && dto.ordinalWeek() == null);
			case EXPLICIT_DATE ->
				require(hasExplicitDate && !hasWeekOffset && !hasMonthOffset && !hasMonth && dto.ordinalWeek() == null);
			case RELATIVE_WEEK, END_OF_WEEK ->
				require(!hasExplicitDate && hasWeekOffset && !hasMonthOffset && !hasMonth && dto.ordinalWeek() == null);
			case RELATIVE_MONTH -> require(!hasExplicitDate && !hasWeekOffset && hasMonthOffset && !hasMonth);
			case NAMED_MONTH -> require(!hasExplicitDate && !hasWeekOffset && !hasMonthOffset && hasMonth);
		}
	}

	private void applyTimeBounds(RequestAvailabilityWindow window, AIInterpretationResponseDto.TimeWindowDto dto,
			ClinicSettings settings) {
		LocalTime start = null;
		LocalTime end = null;
		if (dto.namedPeriod() != null) {
			require(dto.startTime() == null && dto.endTime() == null);
			String namedPeriod = normalizeEnum(dto.namedPeriod());
			switch (namedPeriod) {
				case "MORNING" -> {
					start = settings.getMorningStartTime();
					end = settings.getMorningEndTime();
				}
				case "AFTERNOON" -> {
					start = settings.getAfternoonStartTime();
					end = settings.getAfternoonEndTime();
				}
				case "EVENING" -> {
					start = settings.getEveningStartTime();
					end = settings.getEveningEndTime();
				}
				default -> throw new IllegalArgumentException("Unknown named period");
			}
			window.setNamedPeriod(namedPeriod);
		}
		else if (dto.startTime() != null || dto.endTime() != null) {
			require(dto.startTime() != null && dto.endTime() != null);
			start = LocalTime.parse(dto.startTime());
			end = LocalTime.parse(dto.endTime());
		}
		if (start != null) {
			require(start.isBefore(end) && timeService.isGridAligned(start) && timeService.isGridAligned(end));
			window.setStartTime(start);
			window.setEndTime(end);
		}
	}

	private String normalizeEnum(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Required enum value is missing");
		}
		return value.toUpperCase(Locale.ROOT);
	}

	private void require(boolean condition) {
		if (!condition) {
			throw new IllegalArgumentException("Invalid calendar expression field combination");
		}
	}

	@Transactional
	public void processAIInterpretationFailure(String token, Integer requestId, String failureReason) {
		Optional<OperationToken> opOpt = operationTokenRepository.findByToken(token);
		if (opOpt.isEmpty()) {
			return;
		}
		OperationToken op = opOpt.get();
		if (op.getStatus() != OperationStatus.DISPATCHED) {
			return;
		}

		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || request.getStatus() != RequestStatus.INTERPRETING) {
			return;
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		Instant now = timeService.now();

		if (op.getDeadline() != null && op.getDeadline().isBefore(now)) {
			op.setStatus(OperationStatus.TIMED_OUT);
		}
		else {
			op.setStatus(OperationStatus.FAILED);
		}
		operationTokenRepository.save(op);

		fallbackToStaff(request, "AI_UNAVAILABLE", now, settings);

		auditService.recordEvent(AuditEventType.OPERATION_FAILED, "ai-service", "SYSTEM", "OPERATION", token,
				"AI_UNAVAILABLE", Map.of("reason", failureReason != null ? failureReason : ""));
	}

	@Transactional
	public void disputeClinicalFacts(Integer requestId, Integer ownerId, String reason, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request not found");
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		Instant now = timeService.now();

		fallbackToStaff(request, "CLINICAL_DISPUTE", now, settings);

		auditService.recordEvent(AuditEventType.REQUEST_UPDATED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "CLINICAL_DISPUTE",
				Map.of("reason", reason != null ? reason : ""));
	}

	@Transactional
	public void confirmInterpretation(Integer requestId, Integer ownerId, boolean confirmUnrestricted,
			String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request not found");
		}
		if (request.getStatus() != RequestStatus.AWAITING_CONFIRMATION) {
			throw new IllegalStateException("Request is not awaiting confirmation");
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		Instant now = timeService.now();
		Instant horizonEnd = timeService.calculateOwnerHorizonEnd(now, settings.getOwnerBookingHorizonDays());
		request.setOwnerHorizonEnd(horizonEnd);
		if (availabilityWindowResolver.materialize(request, now,
				horizonEnd) == AvailabilityWindowResolver.MaterializationResult.OUTSIDE_HORIZON) {
			request.setStatus(RequestStatus.CLARIFICATION_REQUIRED);
			request.setLastClarificationReason("OUTSIDE_BOOKING_HORIZON");
			request.setClarificationCount(request.getClarificationCount() + 1);
			schedulingRequestRepository.save(request);
			return;
		}
		request.setStatus(RequestStatus.MATCHING);
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_CONFIRMED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "REQUEST_CONFIRMED",
				Map.of("horizonEnd", horizonEnd.toString()));
	}

	@Transactional
	public void editConfirmedFacts(Integer requestId, Integer ownerId, Integer preferredVetId, Integer durationMinutes,
			Instant preferredStartWindow, Instant preferredEndWindow, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request not found");
		}
		if (request.getStatus() != RequestStatus.AWAITING_CONFIRMATION && request.getStatus() != RequestStatus.MATCHING
				&& request.getStatus() != RequestStatus.SLOT_HELD
				&& request.getStatus() != RequestStatus.CLARIFICATION_REQUIRED) {
			throw new IllegalStateException("Cannot edit confirmed facts for request in status " + request.getStatus());
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);

		// Release any active reservation
		List<Reservation> activeReservations = reservationRepository.findByRequestId(requestId);
		for (Reservation res : activeReservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				res.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(res);
			}
		}

		// Clear prior rejections
		List<Rejection> rejections = rejectionRepository.findByRequestId(requestId);
		rejectionRepository.deleteAll(rejections);

		if (preferredVetId != null) {
			vetRepository.findById(preferredVetId).ifPresent(request::setPreferredVet);
		}
		if (durationMinutes != null) {
			int duration = durationMinutes;
			if (duration < settings.getMinDurationMinutes()) {
				duration = settings.getMinDurationMinutes();
			}
			if (duration > settings.getMaxDurationMinutes()) {
				duration = settings.getMaxDurationMinutes();
			}
			duration = ((duration + 14) / 15) * 15;
			request.setDurationMinutes(duration);
		}
		if (preferredStartWindow != null) {
			request.setPreferredStartWindow(preferredStartWindow);
		}
		if (preferredEndWindow != null) {
			request.setPreferredEndWindow(preferredEndWindow);
		}

		request.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_UPDATED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "FACTS_EDITED", Map.of());
	}

	@Transactional
	public void replaceOriginalText(Integer requestId, Integer ownerId, String newText, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request not found");
		}

		RequestStatus currentStatus = request.getStatus();
		if (currentStatus == RequestStatus.STAFF_QUEUED || currentStatus == RequestStatus.STAFF_OFFERED
				|| currentStatus == RequestStatus.CONFIRMED || currentStatus == RequestStatus.CANCELLED
				|| currentStatus == RequestStatus.EXPIRED) {
			throw new IllegalStateException("Cannot replace text for request in status: " + currentStatus);
		}

		String normalized = Normalizer.normalize(newText != null ? newText : "", Normalizer.Form.NFC);
		normalized = normalized.replace("\r\n", "\n").replace("\r", "\n").trim();
		int codePoints = normalized.codePointCount(0, normalized.length());
		if (codePoints < 1 || codePoints > 2000) {
			throw new IllegalArgumentException("Original text must be between 1 and 2000 code points");
		}

		// Invalidate active operation tokens
		List<OperationToken> tokens = operationTokenRepository.findByRequestId(requestId);
		for (OperationToken token : tokens) {
			if (token.getStatus() == OperationStatus.DISPATCHED) {
				token.setStatus(OperationStatus.CANCELLED);
				operationTokenRepository.save(token);
			}
		}

		// Release active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation res : reservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				res.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(res);
			}
		}

		// Clear prior state
		List<Rejection> rejections = rejectionRepository.findByRequestId(requestId);
		rejectionRepository.deleteAll(rejections);

		request.setOriginalText(normalized);
		request.setAiSummary(null);
		request.setFullAiResponse(null);
		request.setLastClarificationReason(null);
		request.setClarificationCount(0);
		request.setConsentGivenAt(null);
		request.setOwnerHorizonEnd(null);
		request.setPreferredVet(null);
		request.setRequiredSpecialty(null);
		request.setPreferredStartWindow(null);
		request.setPreferredEndWindow(null);
		availabilityIntervalRepository.deleteByRequestId(requestId);
		availabilityWindowRepository.deleteByRequestId(requestId);
		request.setStatus(RequestStatus.AWAITING_CONSENT);

		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_UPDATED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "TEXT_REPLACED", Map.of());
	}

	@Transactional
	public void cancelRequest(Integer requestId, Integer ownerId, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Request not found");
		}

		if (request.getStatus() == RequestStatus.CANCELLED || request.getStatus() == RequestStatus.CONFIRMED
				|| request.getStatus() == RequestStatus.EXPIRED) {
			return; // Idempotent
		}

		// Release active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation res : reservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				res.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(res);
			}
		}

		// Invalidate operation tokens
		List<OperationToken> tokens = operationTokenRepository.findByRequestId(requestId);
		for (OperationToken token : tokens) {
			if (token.getStatus() == OperationStatus.DISPATCHED) {
				token.setStatus(OperationStatus.CANCELLED);
				operationTokenRepository.save(token);
			}
		}

		// Remove staff claim
		Optional<StaffClaim> claim = staffClaimRepository.findByRequestId(requestId);
		claim.ifPresent(staffClaimRepository::delete);

		request.setStatus(RequestStatus.CANCELLED);
		if (request.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
			request.setRetentionDeadline(
					timeService.now().plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
		}
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_CANCELLED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "OWNER_CANCELLED", Map.of());
	}

	/**
	 * Re-locks the request by id and, if it is still {@code MATCHING}, transitions it to
	 * the staff fallback queue. Unlike {@link #fallbackToStaff}, this method manages its
	 * own transaction boundary (via the Spring proxy) so it is safe to call from contexts
	 * with no ambient transaction, such as the async solver worker thread.
	 * @return {@code true} if the request was found and fallback was applied
	 */
	@Transactional
	public boolean fallbackToStaffIfMatching(Integer requestId, String reason, Instant now, ClinicSettings settings) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || request.getStatus() != RequestStatus.MATCHING) {
			return false;
		}
		fallbackToStaff(request, reason, now, settings);
		return true;
	}

	public void fallbackToStaff(SchedulingRequest request, String reason, Instant now, ClinicSettings settings) {
		request.setStatus(RequestStatus.STAFF_QUEUED);
		request.setLastClarificationReason(reason);
		if (request.getFirstQueuedAt() == null) {
			request.setFirstQueuedAt(now);
		}
		if (request.getFallbackDeadline() == null) {
			request.setFallbackDeadline(now.plus(Duration.ofDays(settings.getFallbackDeadlineDays())));
		}
		if (request.getOwnerHorizonEnd() == null) {
			request
				.setOwnerHorizonEnd(timeService.calculateOwnerHorizonEnd(now, settings.getOwnerBookingHorizonDays()));
		}
		if ("URGENCY".equalsIgnoreCase(reason)) {
			request.setUrgent(true);
		}

		// Clear active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(request.getId());
		for (Reservation res : reservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				res.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(res);
			}
		}

		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_QUEUED, "system", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(request.getId()), "STAFF_FALLBACK_ENTERED",
				Map.of("reason", reason != null ? reason : ""));
	}

}
