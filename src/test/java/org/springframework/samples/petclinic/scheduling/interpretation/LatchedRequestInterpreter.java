/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Production-interface test double that exposes the real executor's waiting state. */
public final class LatchedRequestInterpreter implements RequestInterpreter {

	private final CountDownLatch entered = new CountDownLatch(1);

	private final CountDownLatch release = new CountDownLatch(1);

	@Override
	public InterpretationResult interpret(String reasonText, String availabilityText) {
		this.entered.countDown();
		await(this.release, "Latched interpreter was not released");
		return new InterpretationResult("Latched review: " + reasonText, 30, CareType.GENERAL, null, null, false,
				List.of(), "{\"latched\":true}", "latched-test-model", "v1");
	}

	public boolean awaitEntered() throws InterruptedException {
		return this.entered.await(10, TimeUnit.SECONDS);
	}

	public void release() {
		this.release.countDown();
	}

	private static void await(CountDownLatch latch, String message) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException(message);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Latched interpreter interrupted", ex);
		}
	}

}
