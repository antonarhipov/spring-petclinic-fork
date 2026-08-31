package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityPersistenceTests {

	@Autowired
	private ClinicPolicyRepository clinicPolicyRepository;

	@Autowired
	private CalendarStateRepository calendarStateRepository;

	@Autowired
	private CalendarMutationCoordinator calendarMutationCoordinator;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	@Autowired
	private VeterinarianLeaveRepository veterinarianLeaveRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	@Test
	void clinicPolicyPersistsWithCollections() {
		ClinicPolicy policy = this.clinicPolicyRepository.findSingleton().orElseThrow();
		assertThat(policy.getZoneId()).isEqualTo("Europe/Amsterdam");
		assertThat(policy.getAllowedDurations()).contains(15, 30, 45, 60);
		assertThat(policy.getOperatingIntervals()).isNotEmpty();
		assertThat(policy.getNamedPeriods()).isNotEmpty();
	}

	@Test
	void calendarStateRevisionIncrementsUnderCoordinator() {
		Long initialRevision = this.calendarMutationCoordinator.getCurrentRevision();

		this.calendarMutationCoordinator.executeWithLock(() -> {
			// Mutation body
			return "OK";
		});

		Long newRevision = this.calendarMutationCoordinator.getCurrentRevision();
		assertThat(newRevision).isEqualTo(initialRevision + 1);
	}

	@Test
	void availabilityExceptionDayCascadePersistsIntervals() {
		Integer vetId = 2;
		LocalDate date = LocalDate.of(2026, 10, 5);

		AvailabilityExceptionDay exceptionDay = new AvailabilityExceptionDay(vetId, date);
		exceptionDay.addInterval(new AvailabilityExceptionInterval(LocalTime.of(8, 0), LocalTime.of(12, 0)));
		exceptionDay.addInterval(new AvailabilityExceptionInterval(LocalTime.of(13, 0), LocalTime.of(16, 0)));

		AvailabilityExceptionDay saved = this.availabilityExceptionDayRepository.saveAndFlush(exceptionDay);
		assertThat(saved.getId()).isNotNull();

		AvailabilityExceptionDay retrieved = this.availabilityExceptionDayRepository
			.findByVetIdAndLocalDate(vetId, date)
			.orElseThrow();
		assertThat(retrieved.getIntervals()).hasSize(2);

		// Delete exception day cascades to intervals
		this.availabilityExceptionDayRepository.delete(retrieved);
		this.availabilityExceptionDayRepository.flush();

		assertThat(this.availabilityExceptionDayRepository.findByVetIdAndLocalDate(vetId, date)).isEmpty();
	}

	@Test
	void veterinarianLeaveDateRangeQueries() {
		Integer vetId = 3;
		LocalDate start = LocalDate.of(2026, 11, 1);
		LocalDate end = LocalDate.of(2026, 11, 10);

		VeterinarianLeave leave = new VeterinarianLeave(vetId, start, end, "TRAINING");
		this.veterinarianLeaveRepository.saveAndFlush(leave);

		List<VeterinarianLeave> active = this.veterinarianLeaveRepository.findActiveLeaveForVetOnDate(vetId,
				LocalDate.of(2026, 11, 5));
		assertThat(active).hasSize(1);

		List<VeterinarianLeave> outside = this.veterinarianLeaveRepository.findActiveLeaveForVetOnDate(vetId,
				LocalDate.of(2026, 11, 15));
		assertThat(outside).isEmpty();
	}

	@Test
	void clinicClosureDateRangeQueries() {
		LocalDate start = LocalDate.of(2026, 12, 24);
		LocalDate end = LocalDate.of(2026, 12, 26);

		ClinicClosure closure = new ClinicClosure(start, end, "Holidays");
		this.clinicClosureRepository.saveAndFlush(closure);

		List<ClinicClosure> active = this.clinicClosureRepository.findActiveClosuresOnDate(LocalDate.of(2026, 12, 25));
		assertThat(active).hasSize(1);

		List<ClinicClosure> outside = this.clinicClosureRepository.findActiveClosuresOnDate(LocalDate.of(2026, 12, 27));
		assertThat(outside).isEmpty();
	}

}
