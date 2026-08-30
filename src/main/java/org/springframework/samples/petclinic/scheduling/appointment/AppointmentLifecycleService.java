package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentLifecycleService {

	private final AppointmentRepository appointments;

	private final ReservationBlockRepository blocks;

	private final OccupancyQueryService occupancy;

	private final AvailabilityCommandService availability;

	private final AvailabilityRepository policies;

	private final VisitRepository visits;

	private final OwnerRepository owners;

	private final AppointmentAuditService audit;

	private final StaffBookingService booking;

	private final Clock clock;

	public AppointmentLifecycleService(AppointmentRepository appointments, ReservationBlockRepository blocks,
			OccupancyQueryService occupancy, AvailabilityCommandService availability, AvailabilityRepository policies,
			VisitRepository visits, OwnerRepository owners, AppointmentAuditService audit, StaffBookingService booking,
			Clock clock) {
		this.appointments = appointments;
		this.blocks = blocks;
		this.occupancy = occupancy;
		this.availability = availability;
		this.policies = policies;
		this.visits = visits;
		this.owners = owners;
		this.audit = audit;
		this.booking = booking;
		this.clock = clock;
	}

	@Transactional
	public Appointment bookDirect(Long requestId, CandidateSlot slot, BookingAuthorization authorization,
			AppointmentReasonCategory reason) {
		if (!this.availability.veterinarianAvailable(slot.veterinarianId(), slot.startAt(), slot.endAt())) {
			throw new LifecycleException("OUT_OF_AVAILABILITY");
		}
		if (occupied(slot.veterinarianId(), null, slot.startAt(), slot.endAt(), null)) {
			throw new LifecycleException("CONFLICT");
		}
		return this.booking.bookDirect(requestId, slot, authorization, reason.name());
	}

	@Transactional
	public Appointment reschedule(Long appointmentId, CandidateSlot slot, Long actorAccountId,
			AppointmentReasonCategory reason, String note) {
		Appointment appointment = this.appointments.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new LifecycleException("NOT_CONFIRMED");
		}
		if (!this.availability.veterinarianAvailable(slot.veterinarianId(), slot.startAt(), slot.endAt())) {
			throw new LifecycleException("OUT_OF_AVAILABILITY");
		}
		if (occupied(slot.veterinarianId(), appointment.getPetId(), slot.startAt(), slot.endAt(), appointmentId)) {
			throw new LifecycleException("CONFLICT");
		}
		String before = snapshot(appointment);
		this.blocks.deleteAll(this.blocks.findByAppointmentId(appointmentId));
		try {
			Instant cursor = slot.startAt();
			while (cursor.isBefore(slot.endAt())) {
				saveBlock(ReservationResourceType.VETERINARIAN, slot.veterinarianId(), cursor, appointmentId);
				saveBlock(ReservationResourceType.PET, appointment.getPetId(), cursor, appointmentId);
				cursor = cursor.plus(Duration.ofMinutes(15));
			}
			this.blocks.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new LifecycleException("CONFLICT");
		}
		appointment.setVeterinarianId(slot.veterinarianId());
		appointment.setStartAt(slot.startAt());
		appointment.setEndAt(slot.endAt());
		appointment.setStaffReasonCategory(reason.name());
		appointment.setStaffReasonNote(note);
		appointment.setUpdatedAt(Instant.now(this.clock));
		this.audit.record("STAFF", actorAccountId, "RESCHEDULED", appointment, before, snapshot(appointment));
		return appointment;
	}

	@Transactional
	public Appointment cancel(Long appointmentId, Long actorAccountId, String actorType,
			AppointmentReasonCategory reason, String note) {
		Appointment appointment = this.appointments.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new LifecycleException("NOT_CONFIRMED");
		}
		String before = snapshot(appointment);
		this.blocks.deleteAll(this.blocks.findByAppointmentId(appointmentId));
		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointment.setStaffReasonCategory(reason.name());
		appointment.setStaffReasonNote(note);
		appointment.setUpdatedAt(Instant.now(this.clock));
		this.audit.record(actorType, actorAccountId, "CANCELLED", appointment, before, snapshot(appointment));
		return appointment;
	}

	@Transactional
	public Appointment complete(Long appointmentId, Long actorAccountId, String notes) {
		Appointment appointment = this.appointments.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new LifecycleException("NOT_CONFIRMED");
		}
		Instant now = Instant.now(this.clock);
		if (now.isBefore(appointment.getEndAt())) {
			throw new LifecycleException("TOO_EARLY");
		}
		if (this.visits.findByAppointmentId(appointmentId).isPresent()) {
			throw new LifecycleException("VISIT_EXISTS");
		}
		String before = snapshot(appointment);
		Visit visit = new Visit();
		visit.setPetId(appointment.getPetId());
		visit.setAppointmentId(appointmentId);
		visit.setVetId(appointment.getVeterinarianId());
		ZoneId zone = ZoneId.of(this.policies.currentPolicy().getZoneId());
		visit.setDate(LocalDate.ofInstant(appointment.getEndAt(), zone));
		visit.setDescription(notes == null || notes.isBlank() ? "Completed appointment" : notes);
		Owner owner = this.owners.findByPetId(appointment.getPetId()).orElseThrow();
		owner.addVisit(appointment.getPetId(), visit);
		this.owners.save(owner);
		appointment.setStatus(AppointmentStatus.COMPLETED);
		appointment.setStaffReasonCategory(AppointmentReasonCategory.COMPLETED.name());
		appointment.setUpdatedAt(now);
		this.audit.record("STAFF", actorAccountId, "COMPLETED", appointment, before, snapshot(appointment));
		return appointment;
	}

	@Transactional
	public Appointment markNoShow(Long appointmentId, Long actorAccountId, String note) {
		Appointment appointment = this.appointments.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new LifecycleException("NOT_CONFIRMED");
		}
		Instant now = Instant.now(this.clock);
		if (now.isBefore(appointment.getEndAt())) {
			throw new LifecycleException("TOO_EARLY");
		}
		String before = snapshot(appointment);
		this.blocks.deleteAll(this.blocks.findByAppointmentId(appointmentId));
		appointment.setStatus(AppointmentStatus.NO_SHOW);
		appointment.setStaffReasonCategory(AppointmentReasonCategory.NO_SHOW.name());
		appointment.setStaffReasonNote(note);
		appointment.setUpdatedAt(now);
		this.audit.record("STAFF", actorAccountId, "NO_SHOW", appointment, before, snapshot(appointment));
		return appointment;
	}

	private boolean occupied(int veterinarianId, Integer petId, Instant start, Instant end, Long ignoreAppointmentId) {
		return this.occupancy.activeBlocks().stream().anyMatch(block -> {
			if (ignoreAppointmentId != null) {
				ReservationBlock existing = this.blocks
					.findById(new ReservationBlockId(block.resourceType(), block.resourceId(), block.blockStart()))
					.orElse(null);
				if (existing != null && ignoreAppointmentId.equals(existing.getAppointmentId())) {
					return false;
				}
			}
			boolean time = !block.blockStart().isBefore(start) && block.blockStart().isBefore(end);
			if (!time) {
				return false;
			}
			if (block.resourceType() == ReservationResourceType.VETERINARIAN && block.resourceId() == veterinarianId) {
				return true;
			}
			return petId != null && block.resourceType() == ReservationResourceType.PET && block.resourceId() == petId;
		});
	}

	private void saveBlock(ReservationResourceType type, int resourceId, Instant start, Long appointmentId) {
		ReservationBlock block = new ReservationBlock();
		block.setResourceType(type);
		block.setResourceId(resourceId);
		block.setBlockStart(start);
		block.setAppointmentId(appointmentId);
		this.blocks.save(block);
	}

	private String snapshot(Appointment appointment) {
		return "{\"id\":" + appointment.getId() + ",\"status\":\"" + appointment.getStatus() + "\",\"start\":\""
				+ appointment.getStartAt() + "\",\"end\":\"" + appointment.getEndAt() + "\"}";
	}

}
