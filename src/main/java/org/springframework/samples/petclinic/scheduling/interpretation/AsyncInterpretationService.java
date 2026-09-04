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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static org.springframework.samples.petclinic.scheduling.config.InterpretationExecutorConfiguration.EXECUTOR_NAME;

/** Dispatches one asynchronous interpretation job per request id. */
@Service
public class AsyncInterpretationService {

	private final RequestInterpretationService interpretationService;

	private final ObjectProvider<AsyncInterpretationService> selfProvider;

	private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

	public AsyncInterpretationService(RequestInterpretationService interpretationService,
			ObjectProvider<AsyncInterpretationService> selfProvider) {
		this.interpretationService = interpretationService;
		this.selfProvider = selfProvider;
	}

	/** Claims the request synchronously before crossing the executor boundary. */
	public boolean dispatch(Integer requestId, String actor) {
		if (!this.inFlight.add(requestId)) {
			return false;
		}
		try {
			this.selfProvider.getObject().interpret(requestId, actor);
			return true;
		}
		catch (RuntimeException ex) {
			this.inFlight.remove(requestId);
			throw ex;
		}
	}

	/**
	 * The asynchronous entry point deliberately accepts only the request id and actor.
	 */
	@Async(EXECUTOR_NAME)
	public void interpret(Integer requestId, String actor) {
		try {
			try {
				this.interpretationService.inputFor(requestId).ifPresent(input -> {
					InterpretationResult result = this.interpretationService.interpret(input);
					this.interpretationService.applyResult(requestId, result, actor);
				});
			}
			catch (ModelUnavailableException ex) {
				this.interpretationService.applyModelUnavailable(requestId, ex.getMessage(), actor);
			}
		}
		finally {
			this.inFlight.remove(requestId);
		}
	}

	boolean isInFlight(Integer requestId) {
		return this.inFlight.contains(requestId);
	}

}
