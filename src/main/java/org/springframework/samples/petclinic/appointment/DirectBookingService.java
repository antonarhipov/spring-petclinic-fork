package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DirectBookingService {

	private static final Set<String> REASON_CATEGORIES = Set.of("OWNER_REQUEST", "CLINICAL_NEED", "FOLLOW_UP",
			"URGENT_STAFF_BOOKING", "LEGACY_RECONCILIATION", "CLINIC_RECOVERY", "OTHER");

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository appointmentChangeEventRepository;

	private final CalendarMutationCoordinator calendarMutationCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final ProtectedPayloadService protectedPayloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	public DirectBookingService(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository appointmentChangeEventRepository,
			CalendarMutationCoordinator calendarMutationCoordinator, CapacityConflictService capacityConflictService,
			EffectiveAvailabilityService effectiveAvailabilityService, OwnerRepository ownerRepository,
			VetRepository vetRepository, ProtectedPayloadService protectedPayloadService, AuditService auditService,
			OwnerHistoryService ownerHistoryService) {
		this.appointmentRepository = appointmentRepository;
		this.appointmentChangeEventRepository = appointmentChangeEventRepository;
		this.calendarMutationCoordinator = calendarMutationCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.protectedPayloadService = protectedPayloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
	}

	public record DirectBookingRequest(Integer ownerId, Integer petId, Integer vetId, Instant startAt, Instant endAt,
			boolean ownerAgreementRecorded, String agreementMedium, String reasonCategory, String internalReason,
			Long actorAccountId, UUID commandId) {
		public DirectBookingRequest(Integer ownerId, Integer petId, Integer vetId, Instant startAt, Instant endAt,
				boolean ownerAgreementRecorded, String agreementMedium, String internalReason, Long actorAccountId,
				UUID commandId) {
			this(ownerId, petId, vetId, startAt, endAt, ownerAgreementRecorded, agreementMedium, "OTHER",
					internalReason, actorAccountId, commandId);
		}
	}

	@Transactional(readOnly = true)
	public BookingConflictCheck validateDirectBooking(DirectBookingRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		if (!request.ownerAgreementRecorded()) {
			return BookingConflictCheck.failed("Owner agreement must be recorded to book directly");
		}
		if (request.internalReason() == null || request.internalReason().isBlank()) {
			return BookingConflictCheck.failed("Internal reason is required to book directly");
		}
		if (!REASON_CATEGORIES.contains(request.reasonCategory())) {
			return BookingConflictCheck.failed("Select a valid booking reason category");
		}
		Owner owner = this.ownerRepository.findById(request.ownerId()).orElse(null);
		if (owner == null) {
			return BookingConflictCheck.failed("Owner not found: " + request.ownerId());
		}
		Pet pet = owner.getPet(request.petId());
		if (pet == null) {
			return BookingConflictCheck.failed("Pet not found or does not belong to owner: " + request.petId());
		}
		Vet vet = this.vetRepository.findById(request.vetId()).orElse(null);
		if (vet == null) {
			return BookingConflictCheck.failed("Veterinarian not found: " + request.vetId());
		}

		return this.capacityConflictService.checkStaffBookingConflicts(request.vetId(), request.ownerId(),
				request.petId(), request.startAt(), request.endAt());
	}

	public Appointment bookDirectly(DirectBookingRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		if (!request.ownerAgreementRecorded()) {
			throw new IllegalArgumentException("Owner agreement must be recorded to book directly");
		}
		if (request.internalReason() == null || request.internalReason().isBlank()) {
			throw new IllegalArgumentException("Internal reason is required to book directly");
		}
		if (!REASON_CATEGORIES.contains(request.reasonCategory())) {
			throw new IllegalArgumentException("Select a valid booking reason category");
		}

		Owner owner = this.ownerRepository.findById(request.ownerId())
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + request.ownerId()));
		Pet pet = owner.getPet(request.petId());
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found or does not belong to owner: " + request.petId());
		}
		Vet vet = this.vetRepository.findById(request.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + request.vetId()));

		return this.calendarMutationCoordinator.executeWithLock(() -> {
			BookingConflictCheck check = this.capacityConflictService.checkStaffBookingConflicts(request.vetId(),
					request.ownerId(), request.petId(), request.startAt(), request.endAt());
			if (!check.valid()) {
				throw new AvailabilityConflictException(
						"Booking conflicts: " + String.join(", ", check.errorMessages()));
			}

			String zoneIdStr = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
			Appointment appointment = new Appointment(request.ownerId(), request.petId(), request.vetId(),
					request.startAt(), request.endAt(), zoneIdStr);
			Appointment saved = this.appointmentRepository.save(appointment);

			Long reasonPayloadId = null;
			if (request.internalReason() != null && !request.internalReason().isBlank()) {
				UUID artifactUuid = UUID.randomUUID();
				ProtectedPayload payload = this.protectedPayloadService.store(artifactUuid, "DIRECT_BOOKING_REASON", 1,
						"text/plain", request.reasonCategory() + ": " + request.internalReason().trim());
				reasonPayloadId = payload.getId();
			}
			String snapshot = "{\"before\":null,\"after\":{\"ownerId\":" + request.ownerId() + ",\"petId\":"
					+ request.petId() + ",\"vetId\":" + request.vetId() + ",\"startAt\":\"" + request.startAt()
					+ "\",\"endAt\":\"" + request.endAt() + "\",\"zoneId\":\"" + zoneIdStr
					+ "\",\"bookingState\":\"CONFIRMED\"}}";
			ProtectedPayload snapshotPayload = this.protectedPayloadService.store(UUID.randomUUID(),
					"DIRECT_BOOKING_SNAPSHOT", 1, "application/json", snapshot);

			AppointmentChangeEvent event = new AppointmentChangeEvent(saved.getId(), request.actorAccountId(), "STAFF",
					"DIRECT_BOOKED", true, request.agreementMedium());
			event.setProtectedReasonPayloadId(reasonPayloadId);
			event.setProtectedSnapshotPayloadId(snapshotPayload.getId());
			this.appointmentChangeEventRepository.save(event);

			this.auditService.recordEvent(request.actorAccountId(), "DIRECT_BOOK_APPOINTMENT", "Appointment",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), request.commandId(),
					snapshotPayload.getId());

			ZoneId zoneId = ZoneId.of(zoneIdStr);
			String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
				.format(ZonedDateTime.ofInstant(request.startAt(), zoneId));
			this.ownerHistoryService.recordEvent(
					request.ownerId(), "APPOINTMENT_CONFIRMED", "Appointment scheduled for " + pet.getName()
							+ " with Dr. " + vet.getLastName() + " at " + formattedTime,
					"Appointment", saved.getId().toString());

			return saved;
		});
	}

}
