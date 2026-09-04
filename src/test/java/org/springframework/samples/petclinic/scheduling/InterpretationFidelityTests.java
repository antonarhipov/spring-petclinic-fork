/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.interpretation.StubRequestInterpreter;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests verifying interpretation fidelity, active request constraints, slot holding and
 * acceptance (RULE-8, RULE-9, RULE-10, RULE-11, RULE-17, RULE-25, AC-20, AC-21, AC-53,
 * AC-58, AC-63).
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class InterpretationFidelityTests {

	@Autowired
	private RequestLifecycleService requestLifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private SuggestionService suggestionService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	@Autowired
	private StubRequestInterpreter interpreter;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private EntityManager entityManager;

	private Owner testOwner;

	private Pet testPet1;

	private Pet testPet2;

	private Vet testVet;

	@BeforeEach
	void setUp() {
		this.interpreter.clear();
		this.testOwner = this.ownerRepository.findById(1).orElseThrow();
		this.testPet1 = this.testOwner.getPet(1);
		this.testVet = this.vetRepository.findAll().iterator().next();
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-53: every interpretation field survives flush and reload")
	void interpretationFidelityRoundTrip() {
		SchedulingRequest request = this.requestLifecycleService.createRequest(this.testOwner, this.testPet1,
				"Dental cleaning and x-ray", "Mondays between 9 and 12", "owner_1");
		this.requestLifecycleService.consent(request, "owner_1");
		List<AvailabilityWindow> windows = List.of(
				AvailabilityWindow.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 15), LocalTime.of(10, 45),
						"Tuesday morning"),
				AvailabilityWindow.allowed(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), LocalTime.of(12, 0),
						LocalTime.of(16, 30), "Wednesday through Friday"),
				AvailabilityWindow.excluded(LocalDate.of(2026, 9, 10), LocalTime.of(13, 15), LocalTime.of(14, 0),
						"not Thursday lunch"));
		InterpretationResult result = new InterpretationResult("Dental care and x-ray", 45, CareType.SPECIALTY,
				"dentistry", this.testVet.getId(), false, windows, "{\"complete\":true}", "stub-model", "1.0");
		this.interpreter.setNextResult(result);
		Interpretation saved = this.interpretationService.interpret(request, "stub");
		this.interpretationRepository.flush();
		this.entityManager.clear();

		Interpretation readBack = this.interpretationRepository.findById(saved.getId()).orElseThrow();
		assertThat(readBack.getVersion()).isEqualTo(1);
		assertThat(readBack.getProvenance()).isEqualTo(Provenance.AI);
		assertThat(readBack.getReasonSummary()).isEqualTo(result.reasonSummary());
		assertThat(readBack.getEstimatedMinutes()).isEqualTo(result.estimatedMinutes());
		assertThat(readBack.getCareType()).isEqualTo(result.careType());
		assertThat(readBack.getSpecialty()).isEqualTo(result.specialty());
		assertThat(readBack.getPreferredVet().getId()).isEqualTo(result.preferredVetId());
		assertThat(readBack.isCannotInterpret()).isEqualTo(result.cannotInterpret());
		assertThat(readBack.getRawResponse()).isEqualTo(result.rawResponse());
		assertThat(readBack.getModelTag()).isEqualTo(result.modelTag());
		assertThat(readBack.getPromptVersion()).isEqualTo(result.promptVersion());
		assertThat(readBack.getWindows()
			.stream()
			.map(window -> new AvailabilityWindow(window.getKind(), window.getDateVal(), window.getStartDate(),
					window.getEndDate(), window.getDayOfWeek(), window.getStartTime(), window.getEndTime(),
					window.getTokens()))
			.toList()).containsExactlyElementsOf(result.windows());
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-20: owned pet starts one awaiting-consent request")
	void startRequestEnforcesSingleActiveRequestPerPet() {
		// First request starts in AWAITING_CONSENT with activePetId set (AC-20)
		SchedulingRequest request1 = this.requestLifecycleService.createRequest(this.testOwner, this.testPet1,
				"Vaccine", "Anytime", "owner_1");
		assertThat(request1.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request1.getActivePetId()).isEqualTo(this.testPet1.getId());

		// Second active request for same pet is rejected (AC-21)
		assertThatThrownBy(() -> this.requestLifecycleService.createRequest(this.testOwner, this.testPet1,
				"Second issue", "Tomorrow", "owner_1"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void confirmAndAcceptFlow() {
		SchedulingRequest request = this.requestLifecycleService.createRequest(this.testOwner, this.testPet1,
				"Routine exam", "Morning", "owner_1");

		this.requestLifecycleService.consent(request, "owner_1");
		this.requestLifecycleService.interpretationUsable(request, "ai");

		// Confirm offers exactly one ranked slot and records hold (AC-58)
		SchedulingRequest offered = this.suggestionService.confirm(request, "owner_1");
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.hasHold()).isTrue();
		assertThat(offered.getHeldVet()).isNotNull();
		assertThat(offered.getHeldStart()).isNotNull();

		// Accept confirms appointment and transitions request to ACCEPTED (AC-63)
		Appointment appointment = this.suggestionService.accept(offered, "owner_1");
		assertThat(appointment).isNotNull();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(appointment.getPet().getId()).isEqualTo(this.testPet1.getId());

		SchedulingRequest completedRequest = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(completedRequest.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(completedRequest.getActivePetId()).isNull();
		assertThat(completedRequest.getHeldVet()).isNull();
	}

}
