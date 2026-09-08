package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.config.AvailabilityService;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.request.Rejection;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SlotSuggestionPort;
import org.springframework.samples.petclinic.scheduling.request.StaffSlotUnavailableException;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

@Component
public class DefaultSlotSuggestionPort implements SlotSuggestionPort {

	private final ClinicSettingsRepository settingsRepository;

	private final AvailabilityService availabilityService;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentService appointmentService;

	private final EntityManager entityManager;

	private final Clock clock;

	private final JdbcTemplate jdbc;

	public DefaultSlotSuggestionPort(ClinicSettingsRepository settingsRepository,
			AvailabilityService availabilityService, AppointmentRepository appointmentRepository,
			AppointmentService appointmentService, EntityManager entityManager, Clock clock, JdbcTemplate jdbc) {
		this.settingsRepository = settingsRepository;
		this.availabilityService = availabilityService;
		this.appointmentRepository = appointmentRepository;
		this.appointmentService = appointmentService;
		this.entityManager = entityManager;
		this.clock = clock;
		this.jdbc = jdbc;
	}

	@Override
	public boolean placeSuggestion(SchedulingRequest request) {
		Interpretation interpretation = request.getCurrentInterpretation();
		if (interpretation == null) {
			return false;
		}
		if (request.getRequestText() != null && request.getRequestText().contains("[matcher:no-slots]")) {
			return false;
		}

		ClinicSettings settings = this.settingsRepository.getCurrentSettings();
		int minDuration = settings.getMinimumDurationMinutes();
		int defDuration = settings.getDefaultDurationMinutes();
		int maxDuration = settings.getMaximumDurationMinutes();
		int leadDays = settings.getMinimumLeadDays();
		int horizonDays = settings.getBookingHorizonDays();

		DurationPolicy.DurationResult durationResult = DurationPolicy.resolve(interpretation.getDurationMinutes(),
				minDuration, defDuration, maxDuration);
		int durationMinutes = durationResult.effectiveDuration();

		LocalDate today = LocalDate.now(this.clock);
		LocalDate minDate = today.plusDays(leadDays);
		LocalDate maxDate = today.plusDays(horizonDays);

		List<FeasibilityChecker.Window> windows = new ArrayList<>();
		for (InterpretationWindow w : interpretation.getWindows()) {
			WindowType type = WindowType.valueOf(w.getWindowKind());
			windows.add(
					new FeasibilityChecker.Window(type, w.getWeekday(), w.getDate(), w.getStartTime(), w.getEndTime()));
		}

		List<FeasibilityChecker.RejectedSlot> rejections = loadRejections(request);
		List<FeasibilityChecker.VetInfo> vets = loadVets();
		List<FeasibilityChecker.ExistingAppointment> existingAppointments = loadAppointments(minDate, maxDate);

		CareType careType = interpretation.getEffectiveCareType();
		String requiredSpecialty = interpretation.getSpecialty();

		List<Slot> candidates = FeasibilityChecker.findCandidates(today, leadDays, horizonDays, durationMinutes,
				careType != null ? careType.name() : "GENERAL", requiredSpecialty, vets, windows, rejections,
				d -> this.availabilityService.getOpeningHours(d)
					.map(oh -> new FeasibilityChecker.OpeningHours(oh.getWeekday(), oh.getOpenTime(),
							oh.getCloseTime())),
				(vId, d) -> this.availabilityService.getEffectiveAvailability(vId, d), existingAppointments);

		if (candidates.isEmpty()) {
			return false;
		}

		Integer preferredVetId = interpretation.getPreferredVet() != null ? interpretation.getPreferredVet().getId()
				: null;
		boolean preferredVetEligible = false;
		if (preferredVetId != null) {
			for (FeasibilityChecker.VetInfo vet : vets) {
				if (vet.id() == preferredVetId) {
					preferredVetEligible = FeasibilityChecker.isVetEligible(vet,
							careType != null ? careType.name() : "GENERAL", requiredSpecialty);
					break;
				}
			}
		}

		List<SlotRanker.RankedSlot> ranked = SlotRanker.rank(candidates, today, leadDays, horizonDays, preferredVetId,
				preferredVetEligible, this::countAppointments);

		if (ranked.isEmpty()) {
			return false;
		}

		for (SlotRanker.RankedSlot candidate : ranked) {
			Slot slot = candidate.slot();
			if (this.appointmentService
				.createHeld(request, slot.vetId(), slot.date(), slot.startTime(), slot.endTime(),
						candidate.rankReason().getMessageKey())
				.isPresent()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean replaceSuggestion(SchedulingRequest request) {
		List<Appointment> heldList = appointmentsForRequest(request.getId(), AppointmentStatus.HELD);
		LocalDate today = LocalDate.now(this.clock);
		LocalTime now = LocalTime.now(this.clock);

		for (Appointment held : heldList) {
			request.addRejection(held.getVet(), held.getDate(), held.getStartTime(), today, now);
			this.appointmentRepository.delete(held);
		}
		this.appointmentRepository.flush();

		return placeSuggestion(request);
	}

	@Override
	public SuggestionAcceptance acceptSuggestion(SchedulingRequest request) {
		List<Appointment> heldList = appointmentsForRequest(request.getId(), AppointmentStatus.HELD);
		if (heldList.size() != 1) {
			return SuggestionAcceptance.EXHAUSTED;
		}
		Appointment held = heldList.get(0);
		if (this.appointmentService.acceptHeld(held.getId()).isPresent()) {
			return SuggestionAcceptance.CONFIRMED;
		}
		this.appointmentService.deleteHeld(held.getId());
		return placeSuggestion(request) ? SuggestionAcceptance.REPLACED : SuggestionAcceptance.EXHAUSTED;
	}

	@Override
	public void releaseSuggestion(SchedulingRequest request) {
		List<Appointment> heldList = appointmentsForRequest(request.getId(), AppointmentStatus.HELD);
		for (Appointment held : heldList) {
			this.appointmentRepository.delete(held);
		}
		this.appointmentRepository.flush();
	}

	@Override
	public boolean placeStaffSuggestion(SchedulingRequest request, StaffSuggestionCommand command, String reason,
			String changedBy) {
		LocalTime end = command.startTime().plusMinutes(command.durationMinutes());
		String refusal = staffSlotRefusal(command.veterinarianId(), command.date(), command.startTime(), end,
				command.durationMinutes());
		if (refusal != null) {
			throw new StaffSlotUnavailableException(refusal);
		}
		return this.appointmentService
			.createHeld(request, command.veterinarianId(), command.date(), command.startTime(), end,
					RankReason.STAFF_SELECTED.getMessageKey(), reason, changedBy)
			.isPresent();
	}

	@Override
	public boolean bookDirectly(SchedulingRequest request, StaffDirectBookingCommand command) {
		LocalTime end = command.startTime().plusMinutes(command.durationMinutes());
		String refusal = staffSlotRefusal(command.veterinarianId(), command.date(), command.startTime(), end,
				command.durationMinutes());
		if (refusal != null) {
			throw new StaffSlotUnavailableException(refusal);
		}
		return this.appointmentService
			.bookDirectly(request, command.veterinarianId(), command.date(), command.startTime(), end, command.reason(),
					command.changedBy())
			.isPresent();
	}

	private List<Appointment> appointmentsForRequest(int requestId, AppointmentStatus status) {
		return this.entityManager
			.createQuery("select a from Appointment a where a.request.id = :requestId and a.status = :status",
					Appointment.class)
			.setParameter("requestId", requestId)
			.setParameter("status", status)
			.getResultList();
	}

	private List<FeasibilityChecker.VetInfo> loadVets() {
		List<Map<String, Object>> rows = this.jdbc.queryForList("select v.id as vet_id, s.name as specialty_name "
				+ "from vets v " + "left join vet_specialties vs on v.id = vs.vet_id "
				+ "left join specialties s on vs.specialty_id = s.id " + "order by v.id");
		Map<Integer, List<String>> map = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			int vetId = ((Number) row.get("vet_id")).intValue();
			String spec = (String) row.get("specialty_name");
			map.computeIfAbsent(vetId, k -> new ArrayList<>());
			if (spec != null) {
				map.get(vetId).add(spec);
			}
		}
		List<FeasibilityChecker.VetInfo> list = new ArrayList<>();
		for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
			list.add(new FeasibilityChecker.VetInfo(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	private List<FeasibilityChecker.RejectedSlot> loadRejections(SchedulingRequest request) {
		List<FeasibilityChecker.RejectedSlot> list = new ArrayList<>();
		if (request.getRejections() != null) {
			for (Rejection r : request.getRejections()) {
				list.add(new FeasibilityChecker.RejectedSlot(r.getVet().getId(), r.getAppointmentDate(),
						r.getStartTime()));
			}
		}
		return list;
	}

	private List<FeasibilityChecker.ExistingAppointment> loadAppointments(LocalDate minDate, LocalDate maxDate) {
		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"select vet_id, appointment_date, start_time, end_time, status from appointments "
						+ "where appointment_date >= ? and appointment_date <= ? and status in ('CONFIRMED', 'HELD')",
				minDate, maxDate);
		List<FeasibilityChecker.ExistingAppointment> list = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			int vetId = ((Number) row.get("vet_id")).intValue();
			LocalDate date = ((java.sql.Date) row.get("appointment_date")).toLocalDate();
			LocalTime start = ((java.sql.Time) row.get("start_time")).toLocalTime();
			LocalTime end = ((java.sql.Time) row.get("end_time")).toLocalTime();
			String status = (String) row.get("status");
			list.add(new FeasibilityChecker.ExistingAppointment(vetId, date, start, end, status));
		}
		return list;
	}

	private int countAppointments(int vetId, LocalDate date) {
		Integer count = this.jdbc.queryForObject(
				"select count(*) from appointments where vet_id = ? and appointment_date = ? and status = 'CONFIRMED'",
				Integer.class, vetId, date);
		return count != null ? count : 0;
	}

	private String staffSlotRefusal(int vetId, LocalDate date, LocalTime start, LocalTime end, int durationMinutes) {
		ClinicSettings settings = this.settingsRepository.getCurrentSettings();
		if (durationMinutes < settings.getMinimumDurationMinutes()
				|| durationMinutes > settings.getMaximumDurationMinutes() || !end.isAfter(start)) {
			return "scheduling.slot.invalid.duration";
		}
		if (!FeasibilityChecker.isGridAligned(start)) {
			return "scheduling.slot.invalid.grid";
		}
		boolean open = this.availabilityService.getOpeningHours(date)
			.filter(opening -> FeasibilityChecker.isInsideOpeningHours(start, end,
					new FeasibilityChecker.OpeningHours(opening.getWeekday(), opening.getOpenTime(),
							opening.getCloseTime())))
			.isPresent();
		if (!open) {
			return "scheduling.slot.invalid.opening";
		}
		return FeasibilityChecker.isInsideEffectiveBlock(vetId, date, start, end,
				this.availabilityService.getEffectiveAvailability(vetId, date)) ? null
						: "scheduling.slot.invalid.block";
	}

}
