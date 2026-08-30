package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_windows")
public class RequestWindow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_revision_id", nullable = false)
	private Long requestRevisionId;

	@Column(nullable = false)
	private String kind;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "source_phrase")
	private String sourcePhrase;

	@Column(name = "named_period_code")
	private String namedPeriodCode;

	@Column(name = "fallback_allowed", nullable = false)
	private boolean fallbackAllowed;

	public Long getId() {
		return this.id;
	}

	public Long getRequestRevisionId() {
		return this.requestRevisionId;
	}

	public void setRequestRevisionId(Long requestRevisionId) {
		this.requestRevisionId = requestRevisionId;
	}

	public String getKind() {
		return this.kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public void setStartAt(Instant startAt) {
		this.startAt = startAt;
	}

	public Instant getEndAt() {
		return this.endAt;
	}

	public void setEndAt(Instant endAt) {
		this.endAt = endAt;
	}

	public String getSourcePhrase() {
		return this.sourcePhrase;
	}

	public void setSourcePhrase(String sourcePhrase) {
		this.sourcePhrase = sourcePhrase;
	}

	public String getNamedPeriodCode() {
		return this.namedPeriodCode;
	}

	public void setNamedPeriodCode(String namedPeriodCode) {
		this.namedPeriodCode = namedPeriodCode;
	}

	public boolean isFallbackAllowed() {
		return this.fallbackAllowed;
	}

	public void setFallbackAllowed(boolean fallbackAllowed) {
		this.fallbackAllowed = fallbackAllowed;
	}

}
