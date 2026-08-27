package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.CalendarConflict;
import org.springframework.samples.petclinic.scheduling.model.ConflictStatus;
import org.springframework.samples.petclinic.scheduling.model.ConflictType;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.CalendarConflictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarConflictService {

	private final CalendarConflictRepository calendarConflictRepository;

	private final AppointmentRepository appointmentRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AuditService auditService;

	private final SchedulingTimeService timeService;

	public CalendarConflictService(CalendarConflictRepository calendarConflictRepository,
			AppointmentRepository appointmentRepository, EffectiveAvailabilityService effectiveAvailabilityService,
			AuditService auditService, SchedulingTimeService timeService) {
		this.calendarConflictRepository = calendarConflictRepository;
		this.appointmentRepository = appointmentRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.auditService = auditService;
		this.timeService = timeService;
	}

	@Transactional
	public void detectAndRecordClosureConflicts(LocalDate startDate, LocalDate endDate, String reason,
			String actorUsername) {
		Instant startInstant = startDate.atStartOfDay(timeService.getClinicZoneId()).toInstant();
		Instant endInstant = endDate.plusDays(1).atStartOfDay(timeService.getClinicZoneId()).toInstant();

		List<Appointment> bookedAppointments = appointmentRepository.findAll()
			.stream()
			.filter(a -> a.getStatus() == AppointmentStatus.BOOKED)
			.filter(a -> a.getStartTime().isBefore(endInstant) && startInstant.isBefore(a.getEndTime()))
			.toList();

		if (!bookedAppointments.isEmpty()) {
			if (reason == null || reason.trim().isEmpty()) {
				throw new IllegalArgumentException("Mandatory reason required for conflicting calendar modification");
			}

			Instant now = timeService.now();
			for (Appointment appt : bookedAppointments) {
				List<CalendarConflict> existing = calendarConflictRepository.findByAppointmentId(appt.getId());
				boolean alreadyRecorded = existing.stream().anyMatch(c -> c.getStatus() == ConflictStatus.UNRESOLVED);
				if (!alreadyRecorded) {
					CalendarConflict conflict = new CalendarConflict();
					conflict.setAppointment(appt);
					conflict.setVet(appt.getVet());
					conflict.setStartTime(appt.getStartTime());
					conflict.setEndTime(appt.getEndTime());
					conflict.setConflictType(ConflictType.CLINIC_CLOSURE);
					conflict.setStatus(ConflictStatus.UNRESOLVED);
					conflict.setDetectedAt(now);
					calendarConflictRepository.save(conflict);

					auditService.recordEvent(AuditEventType.CALENDAR_CONFLICT_DETECTED,
							actorUsername != null ? actorUsername : "staff", "STAFF", "APPOINTMENT",
							String.valueOf(appt.getId()), "CLINIC_CLOSURE",
							Map.of("reason", reason.trim(), "conflictType", "CLINIC_CLOSURE"));
				}
			}
		}
	}

	@Transactional
	public void detectAndRecordVetConflicts(Integer vetId, LocalDate startDate, LocalDate endDate,
			ConflictType conflictType, String reason, String actorUsername) {
		Instant startInstant = startDate.atStartOfDay(timeService.getClinicZoneId()).toInstant();
		Instant endInstant = endDate.plusDays(1).atStartOfDay(timeService.getClinicZoneId()).toInstant();

		List<Appointment> vetAppointments = appointmentRepository
			.findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(vetId, AppointmentStatus.BOOKED, endInstant,
					startInstant);

		List<Appointment> conflicting = vetAppointments.stream()
			.filter(appt -> !effectiveAvailabilityService.isVetAvailable(vetId, appt.getStartTime(), appt.getEndTime()))
			.toList();

		if (!conflicting.isEmpty()) {
			if (reason == null || reason.trim().isEmpty()) {
				throw new IllegalArgumentException("Mandatory reason required for conflicting calendar modification");
			}

			Instant now = timeService.now();
			for (Appointment appt : conflicting) {
				List<CalendarConflict> existing = calendarConflictRepository.findByAppointmentId(appt.getId());
				boolean alreadyRecorded = existing.stream().anyMatch(c -> c.getStatus() == ConflictStatus.UNRESOLVED);
				if (!alreadyRecorded) {
					CalendarConflict conflict = new CalendarConflict();
					conflict.setAppointment(appt);
					conflict.setVet(appt.getVet());
					conflict.setStartTime(appt.getStartTime());
					conflict.setEndTime(appt.getEndTime());
					conflict.setConflictType(conflictType);
					conflict.setStatus(ConflictStatus.UNRESOLVED);
					conflict.setDetectedAt(now);
					calendarConflictRepository.save(conflict);

					auditService.recordEvent(AuditEventType.CALENDAR_CONFLICT_DETECTED,
							actorUsername != null ? actorUsername : "staff", "STAFF", "APPOINTMENT",
							String.valueOf(appt.getId()), conflictType.name(),
							Map.of("reason", reason.trim(), "conflictType", conflictType.name()));
				}
			}
		}
	}

	@Transactional(readOnly = true)
	public List<CalendarConflict> getUnresolvedConflicts() {
		return calendarConflictRepository.findByStatus(ConflictStatus.UNRESOLVED);
	}

	@Transactional
	public void resolveConflictsForAppointment(Integer appointmentId, ConflictStatus newStatus, String actorUsername) {
		if (appointmentId == null || newStatus == null) {
			return;
		}
		List<CalendarConflict> unresolved = calendarConflictRepository.findByAppointmentIdAndStatus(appointmentId,
				ConflictStatus.UNRESOLVED);
		if (unresolved.isEmpty()) {
			return;
		}
		Instant now = timeService.now();
		for (CalendarConflict conflict : unresolved) {
			conflict.setStatus(newStatus);
			conflict.setResolvedAt(now);
			calendarConflictRepository.save(conflict);

			auditService.recordEvent(AuditEventType.CALENDAR_CONFLICT_RESOLVED,
					actorUsername != null ? actorUsername : "system", "STAFF", "CALENDAR_CONFLICT",
					String.valueOf(conflict.getId()), newStatus.name(),
					Map.of("appointmentId", appointmentId, "resolutionStatus", newStatus.name()));
		}
	}

}
