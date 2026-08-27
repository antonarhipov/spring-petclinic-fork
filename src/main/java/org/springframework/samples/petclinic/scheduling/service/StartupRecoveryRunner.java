package org.springframework.samples.petclinic.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StartupRecoveryRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StartupRecoveryRunner.class);

	private final LifecycleProcessor lifecycleProcessor;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final OperationTokenRepository operationTokenRepository;

	private final ReservationRepository reservationRepository;

	private final LockCoordinator lockCoordinator;

	private final AuditService auditService;

	private final ClinicSettingsService clinicSettingsService;

	private final SchedulingTimeService timeService;

	public StartupRecoveryRunner(LifecycleProcessor lifecycleProcessor,
			SchedulingRequestRepository schedulingRequestRepository, OperationTokenRepository operationTokenRepository,
			ReservationRepository reservationRepository, LockCoordinator lockCoordinator, AuditService auditService,
			ClinicSettingsService clinicSettingsService, SchedulingTimeService timeService) {
		this.lifecycleProcessor = lifecycleProcessor;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.operationTokenRepository = operationTokenRepository;
		this.reservationRepository = reservationRepository;
		this.lockCoordinator = lockCoordinator;
		this.auditService = auditService;
		this.clinicSettingsService = clinicSettingsService;
		this.timeService = timeService;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("Starting appointment scheduling crash recovery check...");
		try {
			// 1. Run lifecycle processor to expire overdue holds/offers/deadlines
			lifecycleProcessor.processOverdueDeadlines();

			// 2. Recover interrupted automation operations (INTERPRETING, MATCHING)
			List<Integer> interruptedIds = schedulingRequestRepository.findInterruptedRequestIds();
			for (Integer reqId : interruptedIds) {
				try {
					recoverInterruptedRequest(reqId);
				}
				catch (Exception e) {
					log.error("Failed to recover interrupted request ID: {}", reqId, e);
				}
			}
			log.info("Completed startup crash recovery successfully.");
		}
		catch (Exception e) {
			log.error("Error during startup crash recovery", e);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recoverInterruptedRequest(Integer requestId) {
		SchedulingRequest initial = schedulingRequestRepository.findById(requestId).orElse(null);
		if (initial == null || (initial.getStatus() != RequestStatus.INTERPRETING
				&& initial.getStatus() != RequestStatus.MATCHING)) {
			return;
		}

		Integer ownerId = initial.getOwner() != null ? initial.getOwner().getId() : null;
		Integer petId = initial.getPet() != null ? initial.getPet().getId() : null;
		Integer vetId = initial.getPreferredVet() != null ? initial.getPreferredVet().getId() : null;

		lockCoordinator.lockResources(ownerId != null ? List.of(ownerId) : null, petId != null ? List.of(petId) : null,
				vetId != null ? List.of(vetId) : null, List.of(requestId), null, null);

		SchedulingRequest locked = schedulingRequestRepository.findById(requestId).orElse(null);
		if (locked == null
				|| (locked.getStatus() != RequestStatus.INTERPRETING && locked.getStatus() != RequestStatus.MATCHING)) {
			return;
		}

		Instant now = timeService.now();
		ClinicSettings settings = clinicSettingsService.getSettings();

		// Invalidate active operation tokens
		List<OperationToken> tokens = operationTokenRepository.findByRequestId(requestId);
		for (OperationToken token : tokens) {
			if (token.getStatus() == OperationStatus.DISPATCHED) {
				token.setStatus(OperationStatus.CANCELLED);
				operationTokenRepository.save(token);
			}
		}

		// Clear active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation res : reservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				res.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(res);
			}
		}

		locked.setStatus(RequestStatus.STAFF_QUEUED);
		locked.setRecoveryRequired(true);
		locked.setLastClarificationReason("STARTUP_CRASH_RECOVERY");
		if (locked.getFirstQueuedAt() == null) {
			locked.setFirstQueuedAt(now);
		}
		if (locked.getFallbackDeadline() == null) {
			locked.setFallbackDeadline(now.plus(Duration.ofDays(settings.getFallbackDeadlineDays())));
		}
		if (locked.getOwnerHorizonEnd() == null) {
			locked.setOwnerHorizonEnd(timeService.calculateOwnerHorizonEnd(now, settings.getOwnerBookingHorizonDays()));
		}
		schedulingRequestRepository.save(locked);

		auditService.recordEvent(AuditEventType.OPERATION_FAILED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "STARTUP_CRASH_RECOVERY", Map.of("requestId", String.valueOf(requestId)));

		auditService.recordEvent(AuditEventType.REQUEST_QUEUED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "RECOVERY_REQUIRED", Map.of("requestId", String.valueOf(requestId)));

		auditService.recordEvent(AuditEventType.RECOVERY_EXECUTED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "STARTUP_RECOVERY", Map.of("requestId", String.valueOf(requestId)));

		log.info("Recovered interrupted request ID: {} -> STAFF_QUEUED (recoveryRequired=true)", requestId);
	}

}
