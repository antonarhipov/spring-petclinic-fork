package org.springframework.samples.petclinic.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.dto.SuggestionDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.Rejection;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final AppointmentRepository appointmentRepository;

	private final RejectionRepository rejectionRepository;

	private final StaffClaimRepository staffClaimRepository;

	private final VetRepository vetRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final OperationTokenRepository operationTokenRepository;

	private final AuditService auditService;

	private final NotificationService notificationService;

	private final LockCoordinator lockCoordinator;

	private final LiveFeasibilityService liveFeasibilityService;

	private final SchedulingTimeService timeService;

	public ReservationService(ReservationRepository reservationRepository,
			SchedulingRequestRepository schedulingRequestRepository, AppointmentRepository appointmentRepository,
			RejectionRepository rejectionRepository, StaffClaimRepository staffClaimRepository,
			VetRepository vetRepository, ClinicSettingsRepository clinicSettingsRepository,
			OperationTokenRepository operationTokenRepository, AuditService auditService,
			NotificationService notificationService, LockCoordinator lockCoordinator,
			LiveFeasibilityService liveFeasibilityService, SchedulingTimeService timeService) {
		this.reservationRepository = reservationRepository;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.appointmentRepository = appointmentRepository;
		this.rejectionRepository = rejectionRepository;
		this.staffClaimRepository = staffClaimRepository;
		this.vetRepository = vetRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.operationTokenRepository = operationTokenRepository;
		this.auditService = auditService;
		this.notificationService = notificationService;
		this.lockCoordinator = lockCoordinator;
		this.liveFeasibilityService = liveFeasibilityService;
		this.timeService = timeService;
	}

	@Transactional
	public void commitGuidedHold(String tokenString, Integer requestId, Integer vetId, Instant startTime,
			Instant endTime, String username) {
		Optional<OperationToken> opOpt = operationTokenRepository.findByToken(tokenString);
		if (opOpt.isEmpty()) {
			return;
		}
		OperationToken op = opOpt.get();
		if (op.getStatus() != OperationStatus.DISPATCHED) {
			return;
		}

		Instant now = timeService.now();
		if (op.getDeadline() != null && op.getDeadline().isBefore(now)) {
			op.setStatus(OperationStatus.TIMED_OUT);
			operationTokenRepository.save(op);
			return;
		}

		SchedulingRequest request = schedulingRequestRepository.findById(requestId).orElse(null);
		if (request == null) {
			op.setStatus(OperationStatus.CANCELLED);
			operationTokenRepository.save(op);
			return;
		}

		Integer ownerId = request.getOwner().getId();
		Integer petId = request.getPet().getId();

		// Ordered locks: Owner -> Pet -> Vet -> Request
		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), List.of(requestId), null, null);

		SchedulingRequest lockedRequest = schedulingRequestRepository.findById(requestId).orElse(null);
		if (lockedRequest == null || lockedRequest.getStatus() != RequestStatus.MATCHING) {
			op.setStatus(OperationStatus.CANCELLED);
			operationTokenRepository.save(op);
			return;
		}

		Vet vet = vetRepository.findById(vetId).orElse(null);
		if (vet == null || !vet.isActive()) {
			op.setStatus(OperationStatus.COMPLETED);
			operationTokenRepository.save(op);
			return;
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);

		// Check live feasibility
		boolean feasible = liveFeasibilityService.checkFeasibility(vetId, ownerId, petId, startTime, endTime,
				lockedRequest.getCareType(), lockedRequest.getRequiredSpecialty(), lockedRequest.getOwnerHorizonEnd(),
				requestId);

		if (!feasible) {
			// Concurrency loser: request remains in MATCHING
			op.setStatus(OperationStatus.COMPLETED);
			operationTokenRepository.save(op);
			auditService.recordEvent(AuditEventType.HOLD_CONFLICT_DETECTED, username != null ? username : "solver",
					"SYSTEM", "SCHEDULING_REQUEST", String.valueOf(requestId), "SLOT_TAKEN",
					Map.of("vetId", String.valueOf(vetId), "startTime", startTime.toString()));
			return;
		}

		// Clear prior active reservations for this request
		List<Reservation> activeReservations = reservationRepository.findByRequestId(requestId);
		for (Reservation r : activeReservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				r.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(r);
			}
		}

		Instant expiresAt = now.plus(Duration.ofMinutes(settings.getGuidedHoldDurationMinutes()));

		Reservation hold = new Reservation();
		hold.setRequest(lockedRequest);
		hold.setOwner(lockedRequest.getOwner());
		hold.setPet(lockedRequest.getPet());
		hold.setVet(vet);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setStartTime(startTime);
		hold.setEndTime(endTime);
		hold.setExpiresAt(expiresAt);
		reservationRepository.save(hold);

		lockedRequest.setStatus(RequestStatus.SLOT_HELD);
		schedulingRequestRepository.save(lockedRequest);

		op.setStatus(OperationStatus.COMPLETED);
		operationTokenRepository.save(op);

		auditService.recordEvent(AuditEventType.HOLD_PLACED, username != null ? username : "solver", "SYSTEM",
				"RESERVATION", String.valueOf(hold.getId()), "GUIDED_HOLD_CREATED",
				Map.of("requestId", String.valueOf(requestId), "vetId", String.valueOf(vetId), "startTime",
						startTime.toString(), "expiresAt", expiresAt.toString()));
	}

	@Transactional(readOnly = true)
	public SuggestionDto getGuidedHoldSuggestion(Integer requestId, Integer ownerId) {
		SchedulingRequest request = schedulingRequestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		Reservation activeHold = null;
		for (Reservation r : reservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				activeHold = r;
				break;
			}
		}

		if (activeHold == null) {
			return null;
		}

		Vet vet = activeHold.getVet();
		String vetName = vet.getFirstName() + " " + vet.getLastName();
		List<String> specialties = new ArrayList<>();
		for (Specialty s : vet.getSpecialties()) {
			if (s.isActive()) {
				specialties.add(s.getName());
			}
		}

		int duration = (int) Duration.between(activeHold.getStartTime(), activeHold.getEndTime()).toMinutes();

		return new SuggestionDto(activeHold.getId(), vet.getId(), vetName, specialties, activeHold.getStartTime(),
				activeHold.getEndTime(), duration, activeHold.getExpiresAt(), request.getVersion());
	}

	@Transactional
	public Appointment acceptGuidedHold(Integer requestId, Integer ownerId, Integer reservationId, Long requestVersion,
			String username) {
		SchedulingRequest request = schedulingRequestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		// Idempotency: If request is already CONFIRMED, return existing appointment
		if (request.getStatus() == RequestStatus.CONFIRMED) {
			Optional<Appointment> existingAppt = appointmentRepository.findByRequestId(requestId);
			if (existingAppt.isPresent()) {
				return existingAppt.get();
			}
		}

		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

		Integer petId = request.getPet().getId();
		Integer vetId = reservation.getVet().getId();

		// Ordered locks: Owner -> Pet -> Vet -> Request -> Appointment -> Reservation
		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), List.of(requestId), null,
				List.of(reservationId));

		SchedulingRequest lockedRequest = schedulingRequestRepository.findById(requestId).orElseThrow();
		Reservation lockedReservation = reservationRepository.findById(reservationId).orElseThrow();

		if (lockedRequest.getStatus() == RequestStatus.CONFIRMED) {
			Optional<Appointment> existingAppt = appointmentRepository.findByRequestId(requestId);
			if (existingAppt.isPresent()) {
				return existingAppt.get();
			}
		}

		Instant now = timeService.now();

		if (lockedReservation.getStatus() != ReservationStatus.ACTIVE
				|| (lockedReservation.getExpiresAt() != null && lockedReservation.getExpiresAt().isBefore(now))) {
			throw new IllegalStateException("Hold is no longer current or has expired");
		}

		if (!Objects.equals(lockedReservation.getRequest().getId(), requestId)) {
			throw new IllegalArgumentException("Reservation does not match request");
		}

		lockedReservation.setStatus(ReservationStatus.ACCEPTED);
		reservationRepository.save(lockedReservation);

		BookingSource source = (lockedReservation.getReservationType() == ReservationType.GUIDED_HOLD)
				? BookingSource.GUIDED_MATCHING : BookingSource.STAFF_OFFER;

		Appointment appointment = new Appointment();
		appointment.setRequest(lockedRequest);
		appointment.setOwner(lockedRequest.getOwner());
		appointment.setPet(lockedRequest.getPet());
		appointment.setVet(lockedReservation.getVet());
		appointment.setCareType(lockedRequest.getCareType());
		appointment.setRequiredSpecialty(lockedRequest.getRequiredSpecialty());
		appointment.setStartTime(lockedReservation.getStartTime());
		appointment.setEndTime(lockedReservation.getEndTime());
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setBookingSource(source);
		appointmentRepository.save(appointment);

		lockedRequest.setStatus(RequestStatus.CONFIRMED);
		schedulingRequestRepository.save(lockedRequest);

		// If staff claim exists, remove it
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		claimOpt.ifPresent(staffClaimRepository::delete);

		auditService.recordEvent(AuditEventType.APPOINTMENT_BOOKED, username != null ? username : "owner", "OWNER",
				"APPOINTMENT", String.valueOf(appointment.getId()), "BOOKED",
				Map.of("requestId", String.valueOf(requestId), "vetId", String.valueOf(vetId), "startTime",
						appointment.getStartTime().toString()));

		auditService.recordEvent(AuditEventType.REQUEST_CONFIRMED, username != null ? username : "owner", "OWNER",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "CONFIRMED",
				Map.of("appointmentId", String.valueOf(appointment.getId())));

		notificationService.sendNotification(lockedRequest.getOwner(), "notification.appointmentConfirmed.title",
				"notification.appointmentConfirmed.message", appointment.getStartTime().toString(),
				"/appointments/" + appointment.getId());

		return appointment;
	}

	@Transactional
	public void rejectGuidedHold(Integer requestId, Integer ownerId, Integer reservationId, Long requestVersion,
			String username) {
		SchedulingRequest request = schedulingRequestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

		// Idempotency: If reservation already REJECTED, return
		if (reservation.getStatus() == ReservationStatus.REJECTED) {
			return;
		}

		Integer petId = request.getPet().getId();
		Integer vetId = reservation.getVet().getId();

		// Ordered locks
		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), List.of(requestId), null,
				List.of(reservationId));

		SchedulingRequest lockedRequest = schedulingRequestRepository.findById(requestId).orElseThrow();
		Reservation lockedReservation = reservationRepository.findById(reservationId).orElseThrow();

		if (lockedReservation.getStatus() == ReservationStatus.REJECTED) {
			return;
		}

		if (!Objects.equals(lockedReservation.getRequest().getId(), requestId)) {
			throw new IllegalArgumentException("Reservation does not match request");
		}

		lockedReservation.setStatus(ReservationStatus.REJECTED);
		reservationRepository.save(lockedReservation);

		// Record exact rejected pair if not already recorded
		List<Rejection> existingRejections = rejectionRepository.findByRequestId(requestId);
		boolean alreadyExists = false;
		for (Rejection r : existingRejections) {
			if (Objects.equals(r.getVet().getId(), vetId)
					&& Objects.equals(r.getStartTime(), lockedReservation.getStartTime())) {
				alreadyExists = true;
				break;
			}
		}
		if (!alreadyExists) {
			Rejection rejection = new Rejection();
			rejection.setRequest(lockedRequest);
			rejection.setVet(lockedReservation.getVet());
			rejection.setStartTime(lockedReservation.getStartTime());
			rejection.setEndTime(lockedReservation.getEndTime());
			rejection.setReason("OWNER_REJECTED");
			rejection.setRejectedAt(timeService.now());
			rejectionRepository.save(rejection);
		}

		if (lockedReservation.getReservationType() == ReservationType.GUIDED_HOLD) {
			lockedRequest.setStatus(RequestStatus.MATCHING);
			auditService.recordEvent(AuditEventType.HOLD_REJECTED, username != null ? username : "owner", "OWNER",
					"RESERVATION", String.valueOf(lockedReservation.getId()), "GUIDED_HOLD_REJECTED",
					Map.of("requestId", String.valueOf(requestId), "vetId", String.valueOf(vetId), "startTime",
							lockedReservation.getStartTime().toString()));
		}
		else if (lockedReservation.getReservationType() == ReservationType.STAFF_OFFER) {
			lockedRequest.setStatus(RequestStatus.STAFF_QUEUED);
			auditService.recordEvent(AuditEventType.STAFF_OFFER_REJECTED, username != null ? username : "owner",
					"OWNER", "RESERVATION", String.valueOf(lockedReservation.getId()), "STAFF_OFFER_REJECTED",
					Map.of("requestId", String.valueOf(requestId), "vetId", String.valueOf(vetId), "startTime",
							lockedReservation.getStartTime().toString()));
		}

		schedulingRequestRepository.save(lockedRequest);
	}

}
