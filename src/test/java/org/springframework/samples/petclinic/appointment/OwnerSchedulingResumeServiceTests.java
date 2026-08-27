package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.solver.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.solver.SchedulingCriteria;
import org.springframework.samples.petclinic.scheduling.solver.SuggestionResult;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OwnerSchedulingResumeServiceTests {

	@Mock
	private AppointmentRequestRepository requestRepository;

	@Mock
	private ClinicSettingsRepository settingsRepository;

	@Mock
	private MatchingCoordinator matchingCoordinator;

	private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

	private OwnerSchedulingResumeService service;

	private ClinicSettings settings;

	@BeforeEach
	void setUp() {
		this.settings = new ClinicSettings();
		this.settings.setZoneId("Europe/Amsterdam");
		this.settings.setBookingHorizonDays(3);
		this.service = new OwnerSchedulingResumeService(this.requestRepository, this.settingsRepository,
				this.matchingCoordinator, this.jsonMapper,
				Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void resumeIsOwnerScopedAndRequiresSuggestingState() {
		AppointmentRequest request = request(2, AppointmentRequestStatus.SUGGESTING, validInterpretation());
		given(this.requestRepository.findById(7)).willReturn(Optional.of(request));

		assertThatThrownBy(() -> this.service.resume(1, 7)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does not belong");

		request.getOwner().setId(1);
		request.setStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		assertThatThrownBy(() -> this.service.resume(1, 7)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("not ready");
	}

	@Test
	void resumeExpandsClinicLocalWindowAcrossDstAndRunsMatching() {
		given(this.settingsRepository.getClinicSettings()).willReturn(this.settings);
		AppointmentInterpretation interpretation = validInterpretation();
		AppointmentRequest request = request(1, AppointmentRequestStatus.SUGGESTING, interpretation);
		SuggestionResult expected = SuggestionResult.queued(7, "No slot");
		given(this.requestRepository.findById(7)).willReturn(Optional.of(request));
		given(this.matchingCoordinator.suggest(any(Integer.class), any(SchedulingCriteria.class))).willReturn(expected);

		SuggestionResult result = this.service.resume(1, 7);

		assertThat(result).isSameAs(expected);
		SchedulingCriteria criteria = this.service.toCriteria(interpretation);
		assertThat(criteria.durationMin()).isEqualTo(45);
		assertThat(criteria.allowedWindows()).singleElement().satisfies(window -> {
			assertThat(window.start()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
			assertThat(window.end()).isEqualTo(Instant.parse("2026-10-25T02:30:00Z"));
		});
		verify(this.matchingCoordinator).suggest(7, criteria);
	}

	private AppointmentInterpretation validInterpretation() {
		return new AppointmentInterpretation(
				"exam", "surgery", 45, List.of(), List.of(new InterpretationWindow(DayOfWeek.SUNDAY, null,
						LocalTime.of(2, 30), LocalTime.of(3, 30), this.settings.getZoneId())),
				List.of(), null, Urgency.ROUTINE);
	}

	private AppointmentRequest request(int ownerId, AppointmentRequestStatus status,
			AppointmentInterpretation interpretation) {
		Owner owner = new Owner();
		owner.setId(ownerId);
		AppointmentRequest request = new AppointmentRequest();
		request.setId(7);
		request.setOwner(owner);
		request.setStatus(status);
		request.setInterpretationJson(this.jsonMapper.writeValueAsString(interpretation));
		return request;
	}

}
