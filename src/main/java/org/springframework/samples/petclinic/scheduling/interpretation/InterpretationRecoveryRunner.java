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

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Component;

/** Recovers interpretation work that could not survive an application restart. */
@Component
public class InterpretationRecoveryRunner implements ApplicationRunner {

	private static final String SYSTEM_ACTOR = "system";

	private final SchedulingRequestRepository requestRepository;

	private final RequestLifecycleService lifecycleService;

	public InterpretationRecoveryRunner(SchedulingRequestRepository requestRepository,
			RequestLifecycleService lifecycleService) {
		this.requestRepository = requestRepository;
		this.lifecycleService = lifecycleService;
	}

	@Override
	public void run(ApplicationArguments args) {
		this.requestRepository.findByState(RequestState.INTERPRETING)
			.forEach(request -> this.lifecycleService.systemRestartInterrupted(request, SYSTEM_ACTOR));
	}

}
