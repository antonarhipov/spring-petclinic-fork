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

package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing staff scheduling queues and hold release operations (RULE-29,
 * AC-84..86).
 */
@Service
@Transactional
public class StaffQueueService {

	private final SchedulingRequestRepository requestRepository;

	private final SchedulingRequestEventRepository eventRepository;

	private final RequestLifecycleService lifecycleService;

	private final Clock clock;

	public StaffQueueService(SchedulingRequestRepository requestRepository,
			SchedulingRequestEventRepository eventRepository, RequestLifecycleService lifecycleService, Clock clock) {
		this.requestRepository = requestRepository;
		this.eventRepository = eventRepository;
		this.lifecycleService = lifecycleService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StaffQueueItem> getNeedsStaffQueue() {
		List<SchedulingRequest> requests = this.requestRepository.findByState(RequestState.WITH_STAFF);
		List<SchedulingRequest> sorted = requests.stream()
			.sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
			.toList();

		List<StaffQueueItem> items = new ArrayList<>();
		for (SchedulingRequest request : sorted) {
			String trigger = resolveHandOffTrigger(request);
			Duration age = calculateAge(request);
			items.add(new StaffQueueItem(request, trigger, age, formatHeldSlot(request)));
		}
		return items;
	}

	@Transactional(readOnly = true)
	public List<StaffQueueItem> getAllOpenQueue() {
		List<SchedulingRequest> requests = this.requestRepository.findAllOpen();
		List<StaffQueueItem> items = new ArrayList<>();
		for (SchedulingRequest request : requests) {
			String trigger = resolveHandOffTrigger(request);
			Duration age = calculateAge(request);
			items.add(new StaffQueueItem(request, trigger, age, formatHeldSlot(request)));
		}
		return items;
	}

	public SchedulingRequest releaseHold(Integer requestId, String actor, String reason) {
		SchedulingRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));
		String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";
		String effectiveReason = (reason != null && !reason.isBlank()) ? reason : "staff release hold";
		return this.lifecycleService.staffReleaseHold(request, effectiveActor, effectiveReason);
	}

	public Duration calculateAge(SchedulingRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		return Duration.between(request.getCreatedAt(), now);
	}

	public String resolveHandOffTrigger(SchedulingRequest request) {
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		if (events.isEmpty()) {
			return "Created";
		}
		// Look for the most recent event transitioning to WITH_STAFF
		Optional<SchedulingRequestEvent> handOffEvent = events.stream()
			.filter(e -> e.getToState() == RequestState.WITH_STAFF)
			.reduce((first, second) -> second);

		if (handOffEvent.isPresent()) {
			SchedulingRequestEvent event = handOffEvent.get();
			String action = event.getAction();
			String reason = event.getReason();
			if ("decline consent".equalsIgnoreCase(action)) {
				return "Declined consent";
			}
			if ("route to staff".equalsIgnoreCase(action)) {
				return "Owner routed to staff";
			}
			if ("model unavailable".equalsIgnoreCase(action)) {
				return "Model unavailable";
			}
			if ("unmatched specialty".equalsIgnoreCase(action)) {
				return "Unmatched specialty: " + (reason != null ? reason : "");
			}
			if ("confirm no feasible slots".equalsIgnoreCase(action)) {
				return "No slots available";
			}
			if ("ask for another option — exhausted".equalsIgnoreCase(action)) {
				return "No slots available";
			}
			if ("accept — slot lost none left".equalsIgnoreCase(action)
					|| "view re-validates — none left".equalsIgnoreCase(action)) {
				return "Hold unavailable";
			}
			if ("staff release hold".equalsIgnoreCase(action)) {
				return reason != null ? "Hold released: " + reason : "Hold released";
			}
			if (reason != null && !reason.isBlank()) {
				return reason;
			}
			if (action != null && !action.isBlank()) {
				return action;
			}
		}

		SchedulingRequestEvent lastEvent = events.get(events.size() - 1);
		if (lastEvent.getReason() != null && !lastEvent.getReason().isBlank()) {
			return lastEvent.getReason();
		}
		return lastEvent.getAction();
	}

	private String formatHeldSlot(SchedulingRequest request) {
		if (!request.hasHold()) {
			return "-";
		}
		String vetName = request.getHeldVet() != null
				? request.getHeldVet().getFirstName() + " " + request.getHeldVet().getLastName() : "Vet";
		String dateStr = request.getHeldStart() != null
				? request.getHeldStart().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
		return vetName + " @ " + dateStr + " (" + request.getHeldDuration() + " min)";
	}

	/**
	 * Queue row representation containing request, trigger, age and held slot
	 * description.
	 */
	public static class StaffQueueItem {

		private final SchedulingRequest request;

		private final String trigger;

		private final Duration age;

		private final String heldSlot;

		public StaffQueueItem(SchedulingRequest request, String trigger, Duration age, String heldSlot) {
			this.request = request;
			this.trigger = trigger;
			this.age = age;
			this.heldSlot = heldSlot;
		}

		public SchedulingRequest getRequest() {
			return this.request;
		}

		public String getTrigger() {
			return this.trigger;
		}

		public Duration getAge() {
			return this.age;
		}

		public String getHeldSlot() {
			return this.heldSlot;
		}

		public String getFormattedAge() {
			if (this.age == null) {
				return "-";
			}
			long totalMinutes = Math.max(0, this.age.toMinutes());
			long hours = totalMinutes / 60;
			long minutes = totalMinutes % 60;
			if (hours > 0) {
				return hours + "h " + minutes + "m";
			}
			return minutes + "m";
		}

	}

}
