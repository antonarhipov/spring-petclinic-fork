package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.model.NamedEntity;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;

@Service
public class AIInterpretationService {

	private static final Logger log = LoggerFactory.getLogger(AIInterpretationService.class);

	private final AIInterpretationClient aiClient;

	private final SchedulingRequestService schedulingRequestService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final OperationTokenRepository operationTokenRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	private final SchedulingTimeService timeService;

	private final ThreadPoolExecutor aiExecutor;

	public AIInterpretationService(AIInterpretationClient aiClient, SchedulingRequestService schedulingRequestService,
			SchedulingRequestRepository schedulingRequestRepository, OperationTokenRepository operationTokenRepository,
			ClinicSettingsRepository clinicSettingsRepository, VetRepository vetRepository,
			SpecialtyRepository specialtyRepository, SchedulingTimeService timeService) {
		this.aiClient = aiClient;
		this.schedulingRequestService = schedulingRequestService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.operationTokenRepository = operationTokenRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
		this.timeService = timeService;
		this.aiExecutor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
				new ThreadFactory() {
					private final AtomicInteger count = new AtomicInteger(1);

					@Override
					public Thread newThread(Runnable r) {
						Thread t = new Thread(r, "ai-interpret-" + count.getAndIncrement());
						t.setDaemon(true);
						return t;
					}
				}, new ThreadPoolExecutor.AbortPolicy());
	}

	@PreDestroy
	public void shutdown() {
		aiExecutor.shutdown();
		try {
			if (!aiExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				aiExecutor.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			aiExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	public void asyncInterpret(String token, Integer requestId) {
		try {
			aiExecutor.submit(() -> {
				try {
					Optional<OperationToken> opOpt = operationTokenRepository.findByToken(token);
					if (opOpt.isEmpty() || opOpt.get().getStatus() != OperationStatus.DISPATCHED) {
						return;
					}
					OperationToken op = opOpt.get();
					Instant now = timeService.now();
					if (op.getDeadline() != null && op.getDeadline().isBefore(now)) {
						schedulingRequestService.processAIInterpretationFailure(token, requestId, "AI_UNAVAILABLE");
						return;
					}

					Optional<SchedulingRequest> reqOpt = schedulingRequestRepository.findWithDetailsById(requestId);
					if (reqOpt.isEmpty()) {
						return;
					}
					SchedulingRequest request = reqOpt.get();
					if (request.getStatus() != RequestStatus.INTERPRETING) {
						return;
					}

					ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
					List<String> activeVetNames = vetRepository.findAll()
						.stream()
						.filter(Vet::isActive)
						.map(v -> v.getFirstName() + " " + v.getLastName())
						.toList();
					List<String> activeSpecialties = specialtyRepository.findAll()
						.stream()
						.filter(Specialty::isActive)
						.map(NamedEntity::getName)
						.toList();

					String petTypeName = request.getPet() != null && request.getPet().getType() != null
							? request.getPet().getType().getName() : "pet";

					AIInterpretationResponseDto response = aiClient.interpret(request.getOriginalText(), petTypeName,
							settings, activeVetNames, activeSpecialties);

					schedulingRequestService.processAIInterpretationSuccess(token, requestId, response);
				}
				catch (Exception ex) {
					log.warn("Async AI interpretation failed for request {}: {}", requestId, ex.getMessage());
					schedulingRequestService.processAIInterpretationFailure(token, requestId, ex.getMessage());
				}
			});
		}
		catch (RejectedExecutionException ex) {
			log.warn("AI executor queue full for request {}: {}", requestId, ex.getMessage());
			schedulingRequestService.processAIInterpretationFailure(token, requestId, "AI_UNAVAILABLE");
		}
	}

}
