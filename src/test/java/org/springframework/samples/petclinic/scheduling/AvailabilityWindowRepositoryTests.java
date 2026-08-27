package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CalendarExpressionType;
import org.springframework.samples.petclinic.scheduling.model.OrdinalWeek;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AvailabilityWindowResolver;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AvailabilityWindowRepositoryTests {

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private RequestAvailabilityWindowRepository windowRepository;

	@Autowired
	private RequestAvailabilityIntervalRepository intervalRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Test
	void storesOrderedSymbolicWindowsAndMaterializedIntervals() {
		SchedulingRequest request = saveRequest();
		RequestAvailabilityWindow later = window(request, 2, AvailabilityWindowKind.PREFERRED);
		RequestAvailabilityWindow earlier = window(request, 1, AvailabilityWindowKind.ALLOWED);
		windowRepository.save(later);
		windowRepository.save(earlier);

		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setRequest(request);
		interval.setSourceWindow(earlier);
		interval.setKind(AvailabilityWindowKind.ALLOWED);
		interval.setStartInstant(Instant.parse("2030-09-03T12:00:00Z"));
		interval.setEndInstant(Instant.parse("2030-09-03T17:00:00Z"));
		intervalRepository.saveAndFlush(interval);

		assertThat(windowRepository.findByRequestIdOrderByWindowOrderAscIdAsc(request.getId()))
			.extracting(RequestAvailabilityWindow::getWindowOrder)
			.containsExactly(1, 2);
		assertThat(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(request.getId())).singleElement()
			.satisfies(saved -> {
				assertThat(saved.getKind()).isEqualTo(AvailabilityWindowKind.ALLOWED);
				assertThat(saved.getStartInstant()).isEqualTo(Instant.parse("2030-09-03T12:00:00Z"));
				assertThat(saved.getEndInstant()).isEqualTo(Instant.parse("2030-09-03T17:00:00Z"));
			});
	}

	@Test
	void requestOwnedRowsCanBeReplacedInForeignKeyOrder() {
		SchedulingRequest request = saveRequest();
		RequestAvailabilityWindow window = window(request, 1, AvailabilityWindowKind.EXCLUDED);
		windowRepository.saveAndFlush(window);
		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setRequest(request);
		interval.setSourceWindow(window);
		interval.setKind(AvailabilityWindowKind.EXCLUDED);
		interval.setStartInstant(Instant.parse("2030-09-03T09:00:00Z"));
		interval.setEndInstant(Instant.parse("2030-09-03T10:00:00Z"));
		intervalRepository.saveAndFlush(interval);

		intervalRepository.deleteByRequestId(request.getId());
		intervalRepository.flush();
		windowRepository.deleteByRequestId(request.getId());
		windowRepository.flush();

		assertThat(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(request.getId())).isEmpty();
		assertThat(windowRepository.findByRequestIdOrderByWindowOrderAscIdAsc(request.getId())).isEmpty();
	}

	@Test
	void materializesPersistedNextWeekThursdayAfternoonWithoutLosingCalendarMeaning() {
		SchedulingRequest request = saveRequest();
		RequestAvailabilityWindow window = window(request, 0, AvailabilityWindowKind.ALLOWED);
		window.setExpressionType(CalendarExpressionType.RELATIVE_WEEK);
		window.setWeekOffset(1);
		window.setMonthOffset(null);
		window.setOrdinalWeek(null);
		window.setDayOfWeek(4);
		windowRepository.saveAndFlush(window);
		Instant confirmation = Instant.parse("2026-08-27T10:33:05Z");
		AvailabilityWindowResolver resolver = new AvailabilityWindowResolver(
				new SchedulingTimeService(Clock.fixed(confirmation, ZoneId.of("UTC")), ZoneId.of("UTC")),
				windowRepository, intervalRepository);

		assertThat(resolver.materialize(request, confirmation, Instant.parse("2026-10-22T00:00:00Z")))
			.isEqualTo(AvailabilityWindowResolver.MaterializationResult.SUCCESS);
		assertThat(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(request.getId())).singleElement()
			.satisfies(interval -> {
				assertThat(interval.getStartInstant()).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
				assertThat(interval.getEndInstant()).isEqualTo(Instant.parse("2026-09-03T17:00:00Z"));
			});
	}

	private RequestAvailabilityWindow window(SchedulingRequest request, int order, AvailabilityWindowKind kind) {
		RequestAvailabilityWindow window = new RequestAvailabilityWindow();
		window.setRequest(request);
		window.setWindowOrder(order);
		window.setKind(kind);
		window.setExpressionType(CalendarExpressionType.RELATIVE_MONTH);
		window.setMonthOffset(1);
		window.setOrdinalWeek(OrdinalWeek.SECOND);
		window.setStartTime(LocalTime.NOON);
		window.setEndTime(LocalTime.of(17, 0));
		window.setNamedPeriod("AFTERNOON");
		return window;
	}

	private SchedulingRequest saveRequest() {
		Owner owner = ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().getFirst();
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		request.setOriginalText("Next month in the second week after lunch");
		request.setLanguage("en");
		return requestRepository.saveAndFlush(request);
	}

}
