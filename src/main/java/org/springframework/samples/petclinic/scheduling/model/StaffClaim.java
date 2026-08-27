package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_claims")
public class StaffClaim extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false, unique = true)
	private SchedulingRequest request;

	@Column(name = "staff_username", nullable = false, length = 100)
	private String staffUsername;

	@Column(name = "claimed_at", nullable = false)
	private Instant claimedAt;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "reclaimable_after", nullable = false)
	private Instant reclaimableAfter;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	public StaffClaim() {
	}

	@PrePersist
	public void onPrePersist() {
		if (this.claimedAt == null) {
			this.claimedAt = Instant.now();
		}
		if (this.lastActivityAt == null) {
			this.lastActivityAt = this.claimedAt;
		}
		if (this.reclaimableAfter == null && this.claimedAt != null) {
			this.reclaimableAfter = this.claimedAt.plusSeconds(30 * 60);
		}
	}

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public String getStaffUsername() {
		return staffUsername;
	}

	public void setStaffUsername(String staffUsername) {
		this.staffUsername = staffUsername;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public void setClaimedAt(Instant claimedAt) {
		this.claimedAt = claimedAt;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public void setLastActivityAt(Instant lastActivityAt) {
		this.lastActivityAt = lastActivityAt;
	}

	public Instant getReclaimableAfter() {
		return reclaimableAfter;
	}

	public void setReclaimableAfter(Instant reclaimableAfter) {
		this.reclaimableAfter = reclaimableAfter;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
