package org.springframework.samples.petclinic.availability;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarMutationCoordinator {

	private final CalendarStateRepository calendarStateRepository;

	public CalendarMutationCoordinator(CalendarStateRepository calendarStateRepository) {
		this.calendarStateRepository = calendarStateRepository;
	}

	/**
	 * Acquires a pessimistic write lock on the singleton CalendarState row, executes the
	 * provided mutation callback, increments the calendar revision, and commits.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> T executeWithLock(Supplier<T> action) {
		Objects.requireNonNull(action, "action must not be null");
		CalendarState state = lockCalendarState();
		T result = action.get();
		state.incrementRevision();
		state.setUpdatedAt(Instant.now());
		this.calendarStateRepository.save(state);
		return result;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void executeWithLock(Runnable action) {
		Objects.requireNonNull(action, "action must not be null");
		executeWithLock(() -> {
			action.run();
			return null;
		});
	}

	@Transactional(readOnly = true)
	public Long getCurrentRevision() {
		return this.calendarStateRepository.findById(1).map(CalendarState::getRevision).orElse(0L);
	}

	private CalendarState lockCalendarState() {
		return this.calendarStateRepository.findSingletonForUpdate().orElseGet(() -> {
			CalendarState state = new CalendarState();
			state.setId(1);
			state.setRevision(0L);
			state.setUpdatedAt(Instant.now());
			return this.calendarStateRepository.saveAndFlush(state);
		});
	}

}
