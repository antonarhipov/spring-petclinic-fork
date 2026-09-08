package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Service
public class AppointmentService {

	private static final List<AppointmentStatus> BLOCKING_STATUSES = List.of(AppointmentStatus.HELD,
			AppointmentStatus.CONFIRMED);

	private final AppointmentRepository appointments;

	private final VetRepository vets;

	private final EntityManager entityManager;

	private final Clock clock;

	public AppointmentService(AppointmentRepository appointments, VetRepository vets, EntityManager entityManager,
			Clock clock) {
		this.appointments = appointments;
		this.vets = vets;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional
	public Optional<Appointment> tryBook(int petId, int vetId, LocalDate date, LocalTime startTime, LocalTime endTime,
			String reason, String changedBy) {
		requireReason(reason);
		Vet vet = this.vets.findByIdForUpdate(vetId).orElseThrow(() -> new IllegalArgumentException("Unknown vet"));
		if (!this.appointments.findOverlapping(vetId, date, startTime, endTime, BLOCKING_STATUSES).isEmpty()) {
			return Optional.empty();
		}

		Pet pet = this.entityManager.getReference(Pet.class, petId);
		Appointment appointment = Appointment.confirmed(pet, vet, date, startTime, endTime, reason, changedBy,
				LocalDate.now(this.clock), LocalTime.now(this.clock));
		return Optional.of(this.appointments.saveAndFlush(appointment));
	}

	@Transactional
	public Optional<Appointment> createHeld(SchedulingRequest request, int vetId, LocalDate date, LocalTime startTime,
			LocalTime endTime, String rankReason) {
		return createHeld(request, vetId, date, startTime, endTime, rankReason, null, null);
	}

	@Transactional
	public Optional<Appointment> createHeld(SchedulingRequest request, int vetId, LocalDate date, LocalTime startTime,
			LocalTime endTime, String rankReason, String reason, String changedBy) {
		Vet vet = lockAvailableVet(vetId, date, startTime, endTime, null);
		if (vet == null) {
			return Optional.empty();
		}
		Appointment appointment = Appointment.held(request, vet, date, startTime, endTime, rankReason, reason,
				changedBy, today(), now());
		return Optional.of(this.appointments.saveAndFlush(appointment));
	}

	@Transactional
	public Optional<Appointment> bookDirectly(SchedulingRequest request, int vetId, LocalDate date, LocalTime startTime,
			LocalTime endTime, String reason, String changedBy) {
		requireReason(reason);
		Vet vet = lockAvailableVet(vetId, date, startTime, endTime, null);
		if (vet == null) {
			return Optional.empty();
		}
		Appointment appointment = Appointment.confirmed(request, vet, date, startTime, endTime, reason, changedBy,
				today(), now());
		return Optional.of(this.appointments.saveAndFlush(appointment));
	}

	@Transactional
	public Optional<Appointment> acceptHeld(int appointmentId) {
		Appointment appointment = requireInStatus(appointmentId, "ACCEPT", AppointmentStatus.HELD);
		Vet vet = lockAvailableVet(appointment.getVet().getId(), appointment.getDate(), appointment.getStartTime(),
				appointment.getEndTime(), appointmentId);
		if (vet == null) {
			return Optional.empty();
		}
		appointment.accept();
		return Optional.of(this.appointments.save(appointment));
	}

	@Transactional
	public void deleteHeld(int appointmentId) {
		Appointment appointment = requireInStatus(appointmentId, "DELETE_HOLD", AppointmentStatus.HELD);
		this.appointments.delete(appointment);
	}

	@Transactional
	public Optional<Appointment> reschedule(int appointmentId, int vetId, LocalDate date, LocalTime startTime,
			LocalTime endTime, String reason, String changedBy) {
		requireReason(reason);
		Appointment appointment = requireInStatus(appointmentId, "RESCHEDULE", AppointmentStatus.CONFIRMED);
		Vet vet = lockAvailableVet(vetId, date, startTime, endTime, appointmentId);
		if (vet == null) {
			return Optional.empty();
		}
		appointment.reschedule(vet, date, startTime, endTime, reason, changedBy);
		return Optional.of(this.appointments.save(appointment));
	}

	@Transactional
	public Appointment cancelByOwner(int appointmentId) {
		Appointment appointment = requireInStatus(appointmentId, "OWNER_CANCEL", AppointmentStatus.CONFIRMED);
		return cancelByOwner(appointment);
	}

	@Transactional
	public Appointment cancelByOwner(int ownerId, int appointmentId) {
		Appointment appointment = this.appointments.findByIdAndPetOwnerId(appointmentId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalAppointmentTransitionException(appointment.getStatus(), "OWNER_CANCEL");
		}
		return cancelByOwner(appointment);
	}

	private Appointment cancelByOwner(Appointment appointment) {
		if (!isBeforeStart(appointment)) {
			throw new IllegalAppointmentTransitionException(appointment.getStatus(), "OWNER_CANCEL_AFTER_START");
		}
		appointment.cancel(CancelledBy.OWNER, null, null, today(), now());
		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional
	public Appointment cancelByStaff(int appointmentId, String reason, String changedBy) {
		requireReason(reason);
		Appointment appointment = requireInStatus(appointmentId, "STAFF_CANCEL", AppointmentStatus.CONFIRMED);
		appointment.cancel(CancelledBy.STAFF, reason, changedBy, today(), now());
		return this.appointments.save(appointment);
	}

	@Transactional
	public Appointment complete(int appointmentId, String description) {
		if (description == null || description.isBlank() || description.length() > 255) {
			throw new IllegalArgumentException("A visit description of at most 255 characters is required");
		}
		Appointment appointment = requireInStatus(appointmentId, "COMPLETE", AppointmentStatus.CONFIRMED);
		requireAfterStart(appointment, "COMPLETE_BEFORE_START");
		Visit visit = new Visit();
		visit.setDate(appointment.getDate());
		visit.setDescription(description);
		visit.setAppointment(appointment);
		appointment.getPet().addVisit(visit);
		appointment.complete();
		return this.appointments.save(appointment);
	}

	@Transactional
	public Appointment markNoShow(int appointmentId) {
		Appointment appointment = requireInStatus(appointmentId, "NO_SHOW", AppointmentStatus.CONFIRMED);
		requireAfterStart(appointment, "NO_SHOW_BEFORE_START");
		appointment.markNoShow();
		return this.appointments.save(appointment);
	}

	private Appointment requireInStatus(int appointmentId, String action, AppointmentStatus status) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown appointment " + appointmentId));
		if (appointment.getStatus() != status) {
			throw new IllegalAppointmentTransitionException(appointment.getStatus(), action);
		}
		return appointment;
	}

	private Vet lockAvailableVet(int vetId, LocalDate date, LocalTime startTime, LocalTime endTime,
			Integer appointmentId) {
		Vet vet = this.vets.findByIdForUpdate(vetId).orElseThrow(() -> new IllegalArgumentException("Unknown vet"));
		List<Appointment> overlapping = appointmentId == null
				? this.appointments.findOverlapping(vetId, date, startTime, endTime, BLOCKING_STATUSES)
				: this.appointments.findOverlappingOther(appointmentId, vetId, date, startTime, endTime,
						BLOCKING_STATUSES);
		return overlapping.isEmpty() ? vet : null;
	}

	private boolean isBeforeStart(Appointment appointment) {
		return appointment.isCancellableByOwnerAt(today(), now());
	}

	private void requireAfterStart(Appointment appointment, String action) {
		LocalDate currentDate = today();
		boolean after = currentDate.isAfter(appointment.getDate())
				|| (currentDate.isEqual(appointment.getDate()) && now().isAfter(appointment.getStartTime()));
		if (!after) {
			throw new IllegalAppointmentTransitionException(appointment.getStatus(), action);
		}
	}

	private LocalDate today() {
		return LocalDate.now(this.clock);
	}

	private LocalTime now() {
		return LocalTime.now(this.clock);
	}

	private void requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required");
		}
	}

}
