package org.springframework.samples.petclinic.availability;

import java.util.Collections;
import java.util.List;
import org.springframework.samples.petclinic.appointment.Appointment;

public class AvailabilityConflictException extends RuntimeException {

	private final List<Appointment> conflictingAppointments;

	private final List<AvailabilityBlocker> blockingItems;

	public AvailabilityConflictException(String message) {
		super(message);
		this.conflictingAppointments = Collections.emptyList();
		this.blockingItems = Collections.emptyList();
	}

	public AvailabilityConflictException(String message, List<Appointment> conflictingAppointments) {
		super(message);
		this.conflictingAppointments = conflictingAppointments != null ? conflictingAppointments
				: Collections.emptyList();
		this.blockingItems = this.conflictingAppointments.stream()
			.map(appointment -> new AvailabilityBlocker("APPOINTMENT", appointment.getId(), appointment.getOwnerId(),
					appointment.getPetId(), appointment.getVetId(), appointment.getStartAt(), appointment.getEndAt(),
					"/staff/appointments/" + appointment.getId()))
			.toList();
	}

	public AvailabilityConflictException(String message, List<AvailabilityBlocker> blockingItems,
			boolean structuredBlockers) {
		super(message);
		this.conflictingAppointments = Collections.emptyList();
		this.blockingItems = blockingItems != null ? List.copyOf(blockingItems) : Collections.emptyList();
	}

	public List<Appointment> getConflictingAppointments() {
		return this.conflictingAppointments;
	}

	public List<AvailabilityBlocker> getBlockingItems() {
		return this.blockingItems;
	}

	public record AvailabilityBlocker(String type, Long id, Integer ownerId, Integer petId, Integer vetId,
			java.time.Instant startAt, java.time.Instant endAt, String recoveryPath) {
	}

}
