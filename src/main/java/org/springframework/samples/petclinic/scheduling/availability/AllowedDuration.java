package org.springframework.samples.petclinic.scheduling.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "allowed_durations")
public class AllowedDuration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "policy_id", nullable = false)
	private Long policyId;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	public Long getId() {
		return this.id;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Long getPolicyId() {
		return this.policyId;
	}

	public void setPolicyId(Long policyId) {
		this.policyId = policyId;
	}

}
