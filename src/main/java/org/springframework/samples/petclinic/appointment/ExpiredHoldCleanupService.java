/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredHoldCleanupService {

	private final SlotHoldRepository slotHoldRepository;

	private final AppointmentRequestRepository requestRepository;

	private final Clock clock;

	public ExpiredHoldCleanupService(SlotHoldRepository slotHoldRepository,
			AppointmentRequestRepository requestRepository, Clock clock) {
		this.slotHoldRepository = slotHoldRepository;
		this.requestRepository = requestRepository;
		this.clock = clock;
	}

	@Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
	@Transactional
	public int cleanupExpired() {
		return cleanupExpiredAt(this.clock.instant());
	}

	@Transactional
	public int cleanupExpiredAt(Instant now) {
		List<SlotHold> expired = this.slotHoldRepository.findExpired(now);
		if (expired.isEmpty()) {
			return 0;
		}
		List<Integer> ids = expired.stream().map(SlotHold::getId).toList();
		this.requestRepository.clearActiveHoldIds(ids);
		this.slotHoldRepository.deleteAllInBatch(expired);
		return expired.size();
	}

}
