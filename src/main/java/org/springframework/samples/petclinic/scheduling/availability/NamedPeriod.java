package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "named_periods")
public class NamedPeriod {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "policy_id", nullable = false)
	private Long policyId;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String label;

	@Column(name = "start_local_time", nullable = false)
	private LocalTime startLocalTime;

	@Column(name = "end_local_time", nullable = false)
	private LocalTime endLocalTime;

	public Long getId() {
		return this.id;
	}

	public String getCode() {
		return this.code;
	}

	public String getLabel() {
		return this.label;
	}

	public LocalTime getStartLocalTime() {
		return this.startLocalTime;
	}

	public LocalTime getEndLocalTime() {
		return this.endLocalTime;
	}

	public Long getPolicyId() {
		return this.policyId;
	}

	public void setPolicyId(Long policyId) {
		this.policyId = policyId;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setStartLocalTime(LocalTime startLocalTime) {
		this.startLocalTime = startLocalTime;
	}

	public void setEndLocalTime(LocalTime endLocalTime) {
		this.endLocalTime = endLocalTime;
	}

}
