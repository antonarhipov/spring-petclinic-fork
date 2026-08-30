package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "text_revision_id", nullable = false)
	private Long textRevisionId;

	@Column(nullable = false)
	private String decision;

	@Column(name = "actor_account_id", nullable = false)
	private Long actorAccountId;

	@Column(name = "data_use_copy_version", nullable = false)
	private String dataUseCopyVersion;

	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	public Long getId() {
		return this.id;
	}

	public Long getTextRevisionId() {
		return this.textRevisionId;
	}

	public void setTextRevisionId(Long textRevisionId) {
		this.textRevisionId = textRevisionId;
	}

	public String getDecision() {
		return this.decision;
	}

	public void setDecision(String decision) {
		this.decision = decision;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public String getDataUseCopyVersion() {
		return this.dataUseCopyVersion;
	}

	public void setDataUseCopyVersion(String dataUseCopyVersion) {
		this.dataUseCopyVersion = dataUseCopyVersion;
	}

	public Instant getDecidedAt() {
		return this.decidedAt;
	}

	public void setDecidedAt(Instant decidedAt) {
		this.decidedAt = decidedAt;
	}

}
