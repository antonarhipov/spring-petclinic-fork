package org.springframework.samples.petclinic.scheduling.appointment;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class ReservationBlockId implements Serializable {

	private ReservationResourceType resourceType;

	private Integer resourceId;

	private Instant blockStart;

	public ReservationBlockId() {
	}

	public ReservationBlockId(ReservationResourceType resourceType, Integer resourceId, Instant blockStart) {
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.blockStart = blockStart;
	}

	public ReservationResourceType getResourceType() {
		return this.resourceType;
	}

	public Integer getResourceId() {
		return this.resourceId;
	}

	public Instant getBlockStart() {
		return this.blockStart;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ReservationBlockId other)) {
			return false;
		}
		return this.resourceType == other.resourceType && Objects.equals(this.resourceId, other.resourceId)
				&& Objects.equals(this.blockStart, other.blockStart);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.resourceType, this.resourceId, this.blockStart);
	}

}
