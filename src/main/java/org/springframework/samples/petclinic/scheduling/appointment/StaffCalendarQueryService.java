package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.config.AvailabilityService;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.config.OpeningHours;
import org.springframework.samples.petclinic.scheduling.matching.DurationPolicy;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailability;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffCalendarQueryService {

	private static final int GRID_MINUTES = 15;

	private static final List<AppointmentStatus> CALENDAR_STATUSES = List.of(AppointmentStatus.CONFIRMED,
			AppointmentStatus.HELD);

	private final AppointmentRepository appointments;

	private final VetRepository vets;

	private final OwnerRepository owners;

	private final AvailabilityService availability;

	private final ClinicSettingsRepository settings;

	private final Clock clock;

	public StaffCalendarQueryService(AppointmentRepository appointments, VetRepository vets, OwnerRepository owners,
			AvailabilityService availability, ClinicSettingsRepository settings, Clock clock) {
		this.appointments = appointments;
		this.vets = vets;
		this.owners = owners;
		this.availability = availability;
		this.settings = settings;
		this.clock = clock;
	}

	public CalendarView calendar(LocalDate date) {
		List<VeterinarianView> veterinarianViews = veterinarians(null);
		List<Appointment> dayAppointments = this.appointments.findByDateAndStatusInOrderByVetIdAscStartTimeAsc(date,
				CALENDAR_STATUSES);
		Map<Integer, Owner> ownersByPet = ownersByPet();
		Optional<OpeningHours> opening = this.availability.getOpeningHours(date);
		List<EffectiveAvailability> effectiveAvailability = this.availability.getEffectiveAvailability(date);
		ClinicSettings clinicSettings = this.settings.getCurrentSettings();
		int defaultDuration = DurationPolicy
			.resolve(null, clinicSettings.getMinimumDurationMinutes(), clinicSettings.getDefaultDurationMinutes(),
					clinicSettings.getMaximumDurationMinutes())
			.effectiveDuration();
		List<CalendarRow> rows = opening
			.map(hours -> rows(hours, veterinarianViews, dayAppointments, ownersByPet, effectiveAvailability))
			.orElseGet(List::of);
		return new CalendarView(date, date.minusDays(1), date.plusDays(1), opening.isEmpty(),
				opening.map(OpeningHours::getOpenTime).orElse(null),
				opening.map(OpeningHours::getCloseTime).orElse(null), veterinarianViews, rows,
				petChoices(ownersByPet.values()), defaultDuration, clinicSettings.getMinimumDurationMinutes(),
				clinicSettings.getMaximumDurationMinutes());
	}

	public Optional<AppointmentDetail> detail(int appointmentId) {
		return this.appointments.findById(appointmentId).map(appointment -> {
			Map<Integer, Owner> ownersByPet = ownersByPet();
			Owner owner = ownersByPet.get(appointment.getPet().getId());
			String specialty = requestedSpecialty(appointment);
			LocalDate today = LocalDate.now(this.clock);
			LocalTime now = LocalTime.now(this.clock);
			boolean started = today.isAfter(appointment.getDate())
					|| (today.isEqual(appointment.getDate()) && now.isAfter(appointment.getStartTime()));
			boolean confirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;
			return new AppointmentDetail(appointment.getId(), ownerName(owner), appointment.getPet().getName(),
					appointment.getVet().getFirstName() + " " + appointment.getVet().getLastName(),
					appointment.getDate(), appointment.getStartTime(), appointment.getEndTime(),
					appointment.getDurationMinutes(), statusMessageKey(appointment.getStatus()),
					originMessageKey(appointment), appointment.getLastChangeReason(), appointment.getLastChangedBy(),
					appointment.getCancelledBy(), appointment.getCancelledDate(), appointment.getCancelledTime(),
					appointment.getRequest() == null ? null : appointment.getRequest().getRequestText(),
					appointment.getRequest() == null ? null : appointment.getRequest().getId(),
					appointment.getRequest() == null ? null : appointment.getRequest().getVersion(),
					holdAgeMinutes(appointment), appointment.getStatus() == AppointmentStatus.HELD, confirmed,
					confirmed && started, completionDescription(appointment), specialty, veterinarians(specialty));
		});
	}

	private List<CalendarRow> rows(OpeningHours opening, List<VeterinarianView> veterinarianViews,
			List<Appointment> dayAppointments, Map<Integer, Owner> ownersByPet,
			List<EffectiveAvailability> effectiveAvailability) {
		long rowCount = Duration.between(opening.getOpenTime(), opening.getCloseTime()).toMinutes() / GRID_MINUTES;
		return IntStream.range(0, (int) rowCount).mapToObj(index -> {
			LocalTime time = opening.getOpenTime().plusMinutes((long) index * GRID_MINUTES);
			List<CalendarCell> cells = veterinarianViews.stream()
				.map(vet -> cell(vet.id(), time, dayAppointments, ownersByPet, effectiveAvailability))
				.toList();
			return new CalendarRow(time, cells);
		}).toList();
	}

	private CalendarCell cell(int vetId, LocalTime time, List<Appointment> dayAppointments,
			Map<Integer, Owner> ownersByPet, List<EffectiveAvailability> availability) {
		Appointment appointment = dayAppointments.stream()
			.filter(candidate -> candidate.getVet().getId() == vetId && !time.isBefore(candidate.getStartTime())
					&& time.isBefore(candidate.getEndTime()))
			.findFirst()
			.orElse(null);
		if (appointment != null) {
			Owner owner = ownersByPet.get(appointment.getPet().getId());
			return new CalendarCell(vetId, appointment.getStatus().name(), false,
					!time.equals(appointment.getStartTime()), appointment.getId(), ownerName(owner),
					appointment.getPet().getName(), appointment.getStartTime(), appointment.getEndTime(),
					holdAgeMinutes(appointment));
		}
		boolean available = availability.stream()
			.anyMatch(block -> block.vetId() == vetId && block.contains(time, time.plusMinutes(GRID_MINUTES)));
		return new CalendarCell(vetId, available ? "FREE" : "UNAVAILABLE", available, false, null, null, null, time,
				time.plusMinutes(GRID_MINUTES), null);
	}

	private List<VeterinarianView> veterinarians(String requestedSpecialty) {
		return this.vets.findAll().stream().map(vet -> veterinarian(vet, requestedSpecialty)).toList();
	}

	private VeterinarianView veterinarian(Vet vet, String requestedSpecialty) {
		List<String> specialties = vet.getSpecialties().stream().map(specialty -> specialty.getName()).toList();
		boolean match = requestedSpecialty == null || specialties.contains(requestedSpecialty);
		return new VeterinarianView(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), specialties, match);
	}

	private Map<Integer, Owner> ownersByPet() {
		return this.owners.findAll()
			.stream()
			.flatMap(owner -> owner.getPets().stream().map(pet -> Map.entry(pet.getId(), owner)))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private List<PetChoice> petChoices(Collection<Owner> owners) {
		return owners.stream()
			.distinct()
			.flatMap(owner -> owner.getPets()
				.stream()
				.map(pet -> new PetChoice(pet.getId(), ownerName(owner), pet.getName())))
			.sorted(java.util.Comparator.comparing(PetChoice::ownerName).thenComparing(PetChoice::petName))
			.toList();
	}

	private String requestedSpecialty(Appointment appointment) {
		if (appointment.getRequest() == null) {
			return null;
		}
		Interpretation interpretation = appointment.getRequest().getCurrentInterpretation();
		return interpretation == null ? null : interpretation.getSpecialty();
	}

	private Integer holdAgeMinutes(Appointment appointment) {
		if (appointment.getStatus() != AppointmentStatus.HELD || appointment.getHeldDate() == null
				|| appointment.getHeldTime() == null) {
			return null;
		}
		long minutes = Duration
			.between(LocalDateTime.of(appointment.getHeldDate(), appointment.getHeldTime()),
					LocalDateTime.now(this.clock))
			.toMinutes();
		return (int) Math.max(minutes, 0);
	}

	private String completionDescription(Appointment appointment) {
		if (appointment.getRequest() == null || appointment.getRequest().getRequestText() == null) {
			return "";
		}
		String requestText = appointment.getRequest().getRequestText();
		return requestText.substring(0, Math.min(requestText.length(), 255));
	}

	private String ownerName(Owner owner) {
		return owner == null ? "" : owner.getFirstName() + " " + owner.getLastName();
	}

	private String statusMessageKey(AppointmentStatus status) {
		return switch (status) {
			case HELD -> "scheduling.appointment.status.held";
			case CONFIRMED -> "scheduling.appointment.status.confirmed";
			case CANCELLED -> "scheduling.appointment.status.cancelled";
			case COMPLETED -> "scheduling.appointment.status.completed";
			case NO_SHOW -> "scheduling.appointment.status.noShow";
		};
	}

	private String originMessageKey(Appointment appointment) {
		if (appointment.getRequest() == null) {
			return "scheduling.appointment.origin.direct";
		}
		Interpretation interpretation = appointment.getRequest().getCurrentInterpretation();
		if (interpretation == null) {
			return "scheduling.appointment.origin.request";
		}
		return interpretation.getOrigin() == InterpretationOrigin.AI ? "scheduling.appointment.origin.ai"
				: "scheduling.appointment.origin.staff";
	}

	public record CalendarView(LocalDate date, LocalDate previousDate, LocalDate nextDate, boolean clinicClosed,
			LocalTime openingTime, LocalTime closingTime, List<VeterinarianView> veterinarians, List<CalendarRow> rows,
			List<PetChoice> pets, int defaultDurationMinutes, int minimumDurationMinutes, int maximumDurationMinutes) {
	}

	public record CalendarRow(LocalTime time, List<CalendarCell> cells) {
	}

	public record CalendarCell(int veterinarianId, String kind, boolean available, boolean continuation,
			Integer appointmentId, String ownerName, String petName, LocalTime startTime, LocalTime endTime,
			Integer holdAgeMinutes) {
	}

	public record VeterinarianView(int id, String name, List<String> specialties, boolean specialtyMatch) {
	}

	public record PetChoice(int id, String ownerName, String petName) {
	}

	public record AppointmentDetail(int id, String ownerName, String petName, String veterinarianName, LocalDate date,
			LocalTime startTime, LocalTime endTime, int durationMinutes, String statusMessageKey,
			String originMessageKey, String reason, String changedBy, CancelledBy cancelledBy, LocalDate cancelledDate,
			LocalTime cancelledTime, String requestText, Integer requestId, Long requestVersion, Integer holdAgeMinutes,
			boolean held, boolean changeAvailable, boolean finalizationAvailable, String completionDescription,
			String requestedSpecialty, List<VeterinarianView> veterinarians) {
	}

}
