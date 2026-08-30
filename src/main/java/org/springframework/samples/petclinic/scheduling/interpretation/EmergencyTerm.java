package org.springframework.samples.petclinic.scheduling.interpretation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "emergency_terms")
public class EmergencyTerm {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "policy_id", nullable = false)
	private Long policyId;

	@Column(nullable = false)
	private String term;

	@Column(name = "rule_set_version", nullable = false)
	private String ruleSetVersion;

	public Long getId() {
		return this.id;
	}

	public Long getPolicyId() {
		return this.policyId;
	}

	public void setPolicyId(Long policyId) {
		this.policyId = policyId;
	}

	public String getTerm() {
		return this.term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getRuleSetVersion() {
		return this.ruleSetVersion;
	}

	public void setRuleSetVersion(String ruleSetVersion) {
		this.ruleSetVersion = ruleSetVersion;
	}

}
