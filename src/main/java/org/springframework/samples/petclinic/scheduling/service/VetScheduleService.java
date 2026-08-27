package org.springframework.samples.petclinic.scheduling.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.model.ClinicClosure;
import org.springframework.samples.petclinic.scheduling.model.ConflictType;
import org.springframework.samples.petclinic.scheduling.model.ScheduleExceptionType;
import org.springframework.samples.petclinic.scheduling.model.ShiftType;
import org.springframework.samples.petclinic.scheduling.model.VetSchedule;
import org.springframework.samples.petclinic.scheduling.model.VetScheduleException;
import org.springframework.samples.petclinic.scheduling.repository.ClinicClosureRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleExceptionRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VetScheduleService {

	private final VetScheduleRepository vetScheduleRepository;

	private final VetScheduleExceptionRepository vetScheduleExceptionRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private final VetRepository vetRepository;

	private final CalendarConflictService calendarConflictService;

	private final SchedulingTimeService timeService;

	public VetScheduleService(VetScheduleRepository vetScheduleRepository,
			VetScheduleExceptionRepository vetScheduleExceptionRepository,
			ClinicClosureRepository clinicClosureRepository, VetRepository vetRepository,
			CalendarConflictService calendarConflictService, SchedulingTimeService timeService) {
		this.vetScheduleRepository = vetScheduleRepository;
		this.vetScheduleExceptionRepository = vetScheduleExceptionRepository;
		this.clinicClosureRepository = clinicClosureRepository;
		this.vetRepository = vetRepository;
		this.calendarConflictService = calendarConflictService;
		this.timeService = timeService;
	}

	@Transactional
	public VetSchedule createWeeklySchedule(Integer vetId, int dayOfWeek, LocalTime startTime, LocalTime endTime,
			ShiftType shiftType) {
		if (dayOfWeek < 1 || dayOfWeek > 7) {
			throw new IllegalArgumentException("Day of week must be between 1 (Monday) and 7 (Sunday)");
		}
		if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("Start time must be strictly before end time");
		}
		if (!timeService.isGridAligned(startTime) || !timeService.isGridAligned(endTime)) {
			throw new IllegalArgumentException("Schedule times must be aligned to the 15-minute grid");
		}

		Vet vet = vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		List<VetSchedule> existing = vetScheduleRepository.findByVetIdAndDayOfWeek(vetId, dayOfWeek);
		for (VetSchedule s : existing) {
			if (startTime.isBefore(s.getEndTime()) && s.getStartTime().isBefore(endTime)) {
				throw new IllegalArgumentException("Schedule overlaps with an existing shift on the same day");
			}
		}

		VetSchedule schedule = new VetSchedule();
		schedule.setVet(vet);
		schedule.setDayOfWeekValue(dayOfWeek);
		schedule.setStartTime(startTime);
		schedule.setEndTime(endTime);
		schedule.setShiftType(shiftType != null ? shiftType : ShiftType.REGULAR);

		return vetScheduleRepository.save(schedule);
	}

	@Transactional
	public VetScheduleException createScheduleException(Integer vetId, LocalDate startDate, LocalDate endDate,
			LocalTime startTime, LocalTime endTime, ScheduleExceptionType type, String reason, String actorUsername) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("Start date must not be after end date");
		}
		if (type == null) {
			throw new IllegalArgumentException("Exception type must not be null");
		}

		Vet vet = vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		if (type == ScheduleExceptionType.MODIFIED_HOURS || type == ScheduleExceptionType.EXTRA_HOURS) {
			if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
				throw new IllegalArgumentException("Start time must be strictly before end time");
			}
			if (!timeService.isGridAligned(startTime) || !timeService.isGridAligned(endTime)) {
				throw new IllegalArgumentException("Exception times must be aligned to the 15-minute grid");
			}
		}

		VetScheduleException exception = new VetScheduleException();
		exception.setVet(vet);
		exception.setStartDate(startDate);
		exception.setEndDate(endDate);
		exception.setStartTime(startTime);
		exception.setEndTime(endTime);
		exception.setExceptionType(type);
		exception.setReason(reason);

		VetScheduleException saved = vetScheduleExceptionRepository.save(exception);

		if (type == ScheduleExceptionType.LEAVE) {
			calendarConflictService.detectAndRecordVetConflicts(vetId, startDate, endDate, ConflictType.VET_LEAVE,
					reason, actorUsername);
		}
		else if (type == ScheduleExceptionType.MODIFIED_HOURS) {
			calendarConflictService.detectAndRecordVetConflicts(vetId, startDate, endDate,
					ConflictType.VET_SCHEDULE_CHANGE, reason, actorUsername);
		}

		return saved;
	}

	@Transactional
	public ClinicClosure createClinicClosure(LocalDate startDate, LocalDate endDate, String reason,
			String actorUsername) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("Start date must not be after end date");
		}

		calendarConflictService.detectAndRecordClosureConflicts(startDate, endDate, reason, actorUsername);

		ClinicClosure closure = new ClinicClosure();
		closure.setStartDate(startDate);
		closure.setEndDate(endDate);
		closure.setReason(reason);

		return clinicClosureRepository.save(closure);
	}

}
