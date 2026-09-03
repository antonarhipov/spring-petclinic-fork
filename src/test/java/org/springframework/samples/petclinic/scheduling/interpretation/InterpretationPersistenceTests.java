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

package org.springframework.samples.petclinic.scheduling.interpretation;

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
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for interpretation JPA mapping, repositories, provenance tracking, and
 * StubRequestInterpreter (RULE-1, RULE-2, RULE-4, RULE-8, AC-109, AC-110, AC-137).
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class InterpretationPersistenceTests {

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private InterpretationWindowRepository windowRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private RequestLifecycleService requestLifecycleService;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	private SchedulingRequest testRequest;

	private Vet testVet;

	@BeforeEach
	void setUp() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		this.testRequest = this.requestLifecycleService.createRequest(owner, pet, "Routine checkup and rabies shot",
				"Monday mornings preferred", "owner_1");
		this.testVet = this.vetRepository.findAll().iterator().next();
	}

	@Test
	void stubRequestInterpreterReturnsExpectedResults() {
		StubRequestInterpreter interpreter = new StubRequestInterpreter();

		InterpretationResult general = interpreter.interpret("Routine checkup", "Monday mornings");
		assertThat(general.cannotInterpret()).isFalse();
		assertThat(general.careType()).isEqualTo(CareType.GENERAL);
		assertThat(general.estimatedMinutes()).isEqualTo(30);
		assertThat(general.windows()).hasSize(1);
		assertThat(general.windows().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

		InterpretationResult surgery = interpreter.interpret("Knee surgery needed", "Friday");
		assertThat(surgery.careType()).isEqualTo(CareType.SPECIALTY);
		assertThat(surgery.specialty()).isEqualTo("surgery");
		assertThat(surgery.estimatedMinutes()).isEqualTo(60);

		InterpretationResult contradiction = interpreter.interpret("cannot_interpret", "contradiction");
		assertThat(contradiction.cannotInterpret()).isTrue();
		assertThat(contradiction.estimatedMinutes()).isEqualTo(30);
	}

	@Test
	void persistsAiInterpretationWithWindows() {
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		Interpretation interpretation = new Interpretation();
		interpretation.setRequest(this.testRequest);
		interpretation.setVersion(1);
		interpretation.setProvenance(Provenance.AI);
		interpretation.setReasonSummary("Annual wellness exam");
		interpretation.setEstimatedMinutes(30);
		interpretation.setCareType(CareType.GENERAL);
		interpretation.setCannotInterpret(false);
		interpretation.setRawResponse("{\"summary\": \"Annual wellness exam\"}");
		interpretation.setModelTag("qwen2.5:32b");
		interpretation.setPromptVersion("v1.0");
		interpretation.setCreatedAt(now);

		InterpretationWindow window = new InterpretationWindow();
		window.setKind(WindowKind.PREFERRED);
		window.setDayOfWeek(DayOfWeek.MONDAY);
		window.setStartTime(LocalTime.of(9, 0));
		window.setEndTime(LocalTime.of(12, 0));
		window.setTokens("Monday morning");
		interpretation.addWindow(window);

		Interpretation saved = this.interpretationRepository.saveAndFlush(interpretation);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getWindows()).hasSize(1);
		assertThat(saved.getWindows().get(0).getId()).isNotNull();

		List<Interpretation> list = this.interpretationRepository
			.findByRequestIdOrderByVersionDesc(this.testRequest.getId());
		assertThat(list).hasSize(1);
		assertThat(list.get(0).getProvenance()).isEqualTo(Provenance.AI);
		assertThat(list.get(0).getVersion()).isEqualTo(1);
		assertThat(list.get(0).getWindows().get(0).getKind()).isEqualTo(WindowKind.PREFERRED);
	}

	@Test
	void persistsStaffInterpretationWithIncrementedVersion() {
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// Version 1 (AI)
		Interpretation aiInterp = new Interpretation();
		aiInterp.setRequest(this.testRequest);
		aiInterp.setVersion(1);
		aiInterp.setProvenance(Provenance.AI);
		aiInterp.setReasonSummary("AI guess");
		aiInterp.setEstimatedMinutes(30);
		aiInterp.setCareType(CareType.GENERAL);
		aiInterp.setCannotInterpret(false);
		aiInterp.setCreatedAt(now.minusMinutes(10));
		this.interpretationRepository.saveAndFlush(aiInterp);

		// Version 2 (Staff override)
		Interpretation staffInterp = new Interpretation();
		staffInterp.setRequest(this.testRequest);
		staffInterp.setVersion(2);
		staffInterp.setProvenance(Provenance.STAFF);
		staffInterp.setReasonSummary("Staff corrected diagnosis: Dental cleaning");
		staffInterp.setEstimatedMinutes(45);
		staffInterp.setCareType(CareType.SPECIALTY);
		staffInterp.setSpecialty("dentistry");
		staffInterp.setPreferredVet(this.testVet);
		staffInterp.setCannotInterpret(false);
		staffInterp.setCreatedAt(now);

		InterpretationWindow staffWindow = new InterpretationWindow();
		staffWindow.setKind(WindowKind.ALLOWED);
		staffWindow.setDateVal(LocalDate.of(2026, 9, 9));
		staffWindow.setStartTime(LocalTime.of(14, 0));
		staffWindow.setEndTime(LocalTime.of(17, 0));
		staffInterp.addWindow(staffWindow);

		this.interpretationRepository.saveAndFlush(staffInterp);

		List<Interpretation> allVersions = this.interpretationRepository
			.findByRequestIdOrderByVersionDesc(this.testRequest.getId());
		assertThat(allVersions).hasSize(2);
		assertThat(allVersions.get(0).getVersion()).isEqualTo(2);
		assertThat(allVersions.get(0).getProvenance()).isEqualTo(Provenance.STAFF);
		assertThat(allVersions.get(0).getSpecialty()).isEqualTo("dentistry");
		assertThat(allVersions.get(1).getVersion()).isEqualTo(1);
		assertThat(allVersions.get(1).getProvenance()).isEqualTo(Provenance.AI);
	}

}
