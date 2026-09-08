package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffQueueQueryService {

	private static final String REQUEST_SELECT = """
			select r.id, r.state, r.version, r.request_text, r.created_date, r.created_time,
			       r.updated_date, r.updated_time, r.with_staff_reason, r.staff_reason,
			       p.id as pet_id, p.name as pet_name, o.id as owner_id,
			       o.first_name as owner_first_name, o.last_name as owner_last_name,
			       i.origin as current_origin,
			       a.id as held_id, a.vet_id as held_vet_id, v.first_name as held_vet_first_name,
			       v.last_name as held_vet_last_name, a.appointment_date as held_date,
			       a.start_time as held_start, a.end_time as held_end,
			       a.held_date as hold_created_date, a.held_time as hold_created_time
			from scheduling_requests r
			join pets p on p.id = r.pet_id
			join owners o on o.id = p.owner_id
			left join interpretations i on i.id = r.current_interpretation_id
			left join appointments a on a.request_id = r.id and a.status = 'HELD'
			left join vets v on v.id = a.vet_id
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public StaffQueueQueryService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public QueueView queue() {
		List<RequestSummary> active = this.jdbc.query(
				REQUEST_SELECT + " where r.active_pet_id is not null order by r.created_date, r.created_time, r.id",
				(result, row) -> {
					return summary(result);
				});
		List<RequestSummary> needsStaff = active.stream()
			.filter(request -> request.state() == RequestState.WITH_STAFF)
			.toList();
		List<RequestSummary> inProgress = active.stream()
			.filter(request -> request.state() != RequestState.WITH_STAFF)
			.toList();
		return new QueueView(needsStaff, inProgress, pets());
	}

	@Transactional(readOnly = true)
	public Optional<RequestDetail> detail(int requestId) {
		return detail(requestId, Locale.ENGLISH);
	}

	@Transactional(readOnly = true)
	public Optional<RequestDetail> detail(int requestId, Locale locale) {
		List<RequestSummary> summaries = this.jdbc.query(REQUEST_SELECT + " where r.id = ?",
				(result, row) -> summary(result), requestId);
		if (summaries.isEmpty()) {
			return Optional.empty();
		}
		List<InterpretationView> versions = interpretations(requestId, locale != null ? locale : Locale.ENGLISH);
		InterpretationView current = versions.stream().filter(InterpretationView::current).findFirst().orElse(null);
		List<InterpretationView> history = versions.stream().filter(version -> !version.current()).toList();
		List<VeterinarianOption> vets = veterinarians(current);
		return Optional
			.of(new RequestDetail(summaries.get(0), current, history, vets, specialties(), isComplete(current)));
	}

	private RequestSummary summary(java.sql.ResultSet result) throws java.sql.SQLException {
		LocalDate holdDate = result.getObject("hold_created_date", LocalDate.class);
		LocalTime holdTime = result.getObject("hold_created_time", LocalTime.class);
		HeldSlotView held = null;
		Integer heldId = result.getObject("held_id", Integer.class);
		if (heldId != null) {
			long ageMinutes = Math.max(0,
					Duration.between(LocalDateTime.of(holdDate, holdTime), LocalDateTime.now(this.clock)).toMinutes());
			held = new HeldSlotView(heldId, result.getObject("held_vet_id", Integer.class),
					result.getString("held_vet_first_name") + " " + result.getString("held_vet_last_name"),
					result.getObject("held_date", LocalDate.class), result.getObject("held_start", LocalTime.class),
					result.getObject("held_end", LocalTime.class), ageMinutes);
		}
		RequestState state = RequestState.valueOf(result.getString("state"));
		String reason = result.getString("with_staff_reason");
		String origin = result.getString("current_origin");
		return new RequestSummary(result.getInt("id"), state, stateMessageKey(state), result.getLong("version"),
				result.getString("request_text"), result.getObject("created_date", LocalDate.class),
				result.getObject("created_time", LocalTime.class), result.getObject("updated_date", LocalDate.class),
				result.getObject("updated_time", LocalTime.class),
				reason != null ? reasonMessageKey(WithStaffReason.valueOf(reason)) : null,
				result.getString("staff_reason"), result.getInt("owner_id"),
				result.getString("owner_first_name") + " " + result.getString("owner_last_name"),
				result.getInt("pet_id"), result.getString("pet_name"),
				origin != null ? originMessageKey(InterpretationOrigin.valueOf(origin)) : null, held);
	}

	private List<PetOption> pets() {
		return this.jdbc.query("""
				select p.id, p.name, o.id as owner_id, o.first_name, o.last_name
				from pets p join owners o on o.id = p.owner_id
				order by o.last_name, o.first_name, p.name, p.id
				""", (result, row) -> new PetOption(result.getInt("id"), result.getString("name"),
				result.getInt("owner_id"), result.getString("first_name") + " " + result.getString("last_name")));
	}

	private List<InterpretationView> interpretations(int requestId, Locale locale) {
		return this.jdbc.query("""
				select i.*, v.first_name as vet_first_name, v.last_name as vet_last_name,
				       case when r.current_interpretation_id = i.id then true else false end as is_current
				from interpretations i
				join scheduling_requests r on r.id = i.request_id
				left join vets v on v.id = i.preferred_vet_id
				where i.request_id = ? order by i.id desc
				""", (result, row) -> {
			int interpretationId = result.getInt("id");
			List<WindowView> windows = windows(interpretationId, locale);
			Integer vetId = result.getObject("preferred_vet_id", Integer.class);
			return new InterpretationView(interpretationId, result.getBoolean("is_current"),
					InterpretationOrigin.valueOf(result.getString("origin")),
					originMessageKey(InterpretationOrigin.valueOf(result.getString("origin"))),
					result.getBoolean("understood"),
					result.getString("care_type") != null ? CareType.valueOf(result.getString("care_type")) : null,
					careTypeMessageKey(result.getString("care_type")), result.getString("specialty"),
					result.getString("specialty_label"), result.getObject("duration_minutes", Integer.class), vetId,
					vetId != null ? result.getString("vet_first_name") + " " + result.getString("vet_last_name") : null,
					windows.stream().filter(window -> "PREFERRED".equals(window.kind())).toList(),
					windows.stream().filter(window -> "ALLOWED".equals(window.kind())).toList(),
					windows.stream().filter(window -> "EXCLUDED".equals(window.kind())).toList(),
					result.getObject("created_date", LocalDate.class),
					result.getObject("created_time", LocalTime.class));
		}, requestId);
	}

	private List<WindowView> windows(int interpretationId, Locale locale) {
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
		return this.jdbc.query("""
				select window_kind, weekday, window_date, start_time, end_time
				from interpretation_windows where interpretation_id = ? order by id
				""", (result, row) -> {
			DayOfWeek weekday = result.getString("weekday") != null ? DayOfWeek.valueOf(result.getString("weekday"))
					: null;
			LocalDate date = result.getObject("window_date", LocalDate.class);
			String dayOrDate = weekday != null ? weekday.getDisplayName(TextStyle.FULL, locale)
					: date.format(dateFormatter);
			return new WindowView(result.getString("window_kind"), weekday, date,
					result.getObject("start_time", LocalTime.class), result.getObject("end_time", LocalTime.class),
					dayOrDate);
		}, interpretationId);
	}

	private List<VeterinarianOption> veterinarians(InterpretationView current) {
		List<Map<String, Object>> rows = this.jdbc.queryForList("""
				select v.id, v.first_name, v.last_name, s.name as specialty
				from vets v
				left join vet_specialties vs on vs.vet_id = v.id
				left join specialties s on s.id = vs.specialty_id
				order by v.last_name, v.first_name, v.id, s.name
				""");
		Map<Integer, MutableVet> vets = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			int id = ((Number) row.get("ID")).intValue();
			MutableVet vet = vets.computeIfAbsent(id,
					ignored -> new MutableVet(id, row.get("FIRST_NAME") + " " + row.get("LAST_NAME")));
			if (row.get("SPECIALTY") != null) {
				vet.specialties.add(row.get("SPECIALTY").toString());
			}
		}
		return vets.values().stream().map(vet -> {
			boolean specialtyMatch = matches(current, vet);
			return new VeterinarianOption(vet.id, vet.name, List.copyOf(vet.specialties), specialtyMatch,
					specialtyMatch ? "scheduling.staff.vet.matches" : "scheduling.staff.vet.mismatch");
		}).toList();
	}

	private boolean matches(InterpretationView current, MutableVet vet) {
		if (current == null || current.careType() != CareType.SPECIALTY || current.specialty() == null) {
			return true;
		}
		if ("OTHER".equals(current.specialty())) {
			return false;
		}
		return vet.specialties.stream().anyMatch(value -> value.equalsIgnoreCase(current.specialty()));
	}

	private List<String> specialties() {
		List<String> specialties = new ArrayList<>(
				this.jdbc.queryForList("select name from specialties order by name", String.class));
		specialties.add("OTHER");
		return specialties;
	}

	private boolean isComplete(InterpretationView interpretation) {
		return interpretation != null && interpretation.understood() && interpretation.careType() != null
				&& (!interpretation.preferredWindows().isEmpty() || !interpretation.allowedWindows().isEmpty())
				&& (interpretation.careType() != CareType.SPECIALTY || interpretation.specialty() != null)
				&& (!"OTHER".equals(interpretation.specialty()) || interpretation.specialtyLabel() != null);
	}

	private String stateMessageKey(RequestState state) {
		return switch (state) {
			case AWAITING_CONSENT -> "scheduling.request.state.awaitingConsent";
			case INTERPRETING -> "scheduling.request.state.interpreting";
			case INTERPRETATION_FAILED -> "scheduling.request.state.interpretationFailed";
			case INTERPRETED -> "scheduling.request.state.interpreted";
			case SUGGESTION_OFFERED -> "scheduling.request.state.suggestionOffered";
			case WITH_STAFF -> "scheduling.request.state.withStaff";
			case ACCEPTED -> "scheduling.request.state.accepted";
			case ABANDONED -> "scheduling.request.state.abandoned";
		};
	}

	private String reasonMessageKey(WithStaffReason reason) {
		return switch (reason) {
			case DECLINED_CONSENT -> "scheduling.staff.reason.declinedConsent";
			case AI_UNAVAILABLE -> "scheduling.staff.reason.aiUnavailable";
			case UNMATCHED_SPECIALTY -> "scheduling.staff.reason.unmatchedSpecialty";
			case NO_SLOTS -> "scheduling.staff.reason.noSlots";
			case HOLD_RELEASED -> "scheduling.staff.reason.holdReleased";
			case SCHEDULE_CHANGED -> "scheduling.staff.reason.scheduleChanged";
			case STAFF_CREATED -> "scheduling.staff.reason.staffCreated";
			case OWNER_CHOICE -> "scheduling.staff.reason.ownerChoice";
		};
	}

	private String careTypeMessageKey(String careType) {
		if (careType == null) {
			return null;
		}
		return "GENERAL".equals(careType) ? "scheduling.careType.general" : "scheduling.careType.specialty";
	}

	private String originMessageKey(InterpretationOrigin origin) {
		return origin == InterpretationOrigin.AI ? "scheduling.interpretation.origin.ai"
				: "scheduling.interpretation.origin.staff";
	}

	private static final class MutableVet {

		private final int id;

		private final String name;

		private final List<String> specialties = new ArrayList<>();

		private MutableVet(int id, String name) {
			this.id = id;
			this.name = name;
		}

	}

	public record QueueView(List<RequestSummary> needsStaff, List<RequestSummary> inProgress, List<PetOption> pets) {
	}

	public record PetOption(int id, String name, int ownerId, String ownerName) {
	}

	public record RequestDetail(RequestSummary request, InterpretationView current, List<InterpretationView> history,
			List<VeterinarianOption> veterinarians, List<String> specialties, boolean currentComplete) {
	}

	public record RequestSummary(int id, RequestState state, String stateMessageKey, long version, String requestText,
			LocalDate createdDate, LocalTime createdTime, LocalDate updatedDate, LocalTime updatedTime,
			String reasonMessageKey, String staffReason, int ownerId, String ownerName, int petId, String petName,
			String originMessageKey, HeldSlotView heldSlot) {
	}

	public record HeldSlotView(int id, int veterinarianId, String veterinarianName, LocalDate date, LocalTime startTime,
			LocalTime endTime, long ageMinutes) {
	}

	public record InterpretationView(int id, boolean current, InterpretationOrigin origin, String originMessageKey,
			boolean understood, CareType careType, String careTypeMessageKey, String specialty, String specialtyLabel,
			Integer durationMinutes, Integer preferredVetId, String preferredVetName, List<WindowView> preferredWindows,
			List<WindowView> allowedWindows, List<WindowView> excludedWindows, LocalDate createdDate,
			LocalTime createdTime) {
	}

	public record WindowView(String kind, DayOfWeek weekday, LocalDate date, LocalTime startTime, LocalTime endTime,
			String dayOrDate) {

		public String displayValue() {
			return this.dayOrDate + " " + this.startTime + "-" + this.endTime;
		}
	}

	public record VeterinarianOption(int id, String name, List<String> specialties, boolean specialtyMatch,
			String matchMessageKey) {
	}

}
