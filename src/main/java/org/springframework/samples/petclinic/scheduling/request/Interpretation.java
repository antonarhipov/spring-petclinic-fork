package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.audit.ProtectedPayload;

@Entity
@Table(name = "interpretations")
public class Interpretation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "text_revision_id", nullable = false, unique = true)
	private TextRevision textRevision;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 32)
	private InterpretationSource source;

	@Column(name = "model_identifier", length = 128)
	private String modelIdentifier;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "raw_payload_id", unique = true)
	private ProtectedPayload rawPayload;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "validated_payload_id", unique = true)
	private ProtectedPayload validatedPayload;

	@Enumerated(EnumType.STRING)
	@Column(name = "validation_state", nullable = false, length = 32)
	private ValidationState validationState;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Interpretation() {
	}

	public Interpretation(TextRevision textRevision, InterpretationSource source, String modelIdentifier,
			ProtectedPayload rawPayload, ProtectedPayload validatedPayload, ValidationState validationState,
			Instant createdAt) {
		this.textRevision = Objects.requireNonNull(textRevision, "textRevision must not be null");
		this.source = Objects.requireNonNull(source, "source must not be null");
		this.modelIdentifier = modelIdentifier;
		this.rawPayload = rawPayload;
		this.validatedPayload = validatedPayload;
		this.validationState = Objects.requireNonNull(validationState, "validationState must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TextRevision getTextRevision() {
		return this.textRevision;
	}

	public void setTextRevision(TextRevision textRevision) {
		this.textRevision = textRevision;
	}

	public InterpretationSource getSource() {
		return this.source;
	}

	public void setSource(InterpretationSource source) {
		this.source = source;
	}

	public String getModelIdentifier() {
		return this.modelIdentifier;
	}

	public void setModelIdentifier(String modelIdentifier) {
		this.modelIdentifier = modelIdentifier;
	}

	public ProtectedPayload getRawPayload() {
		return this.rawPayload;
	}

	public void setRawPayload(ProtectedPayload rawPayload) {
		this.rawPayload = rawPayload;
	}

	public ProtectedPayload getValidatedPayload() {
		return this.validatedPayload;
	}

	public void setValidatedPayload(ProtectedPayload validatedPayload) {
		this.validatedPayload = validatedPayload;
	}

	public ValidationState getValidationState() {
		return this.validationState;
	}

	public void setValidationState(ValidationState validationState) {
		this.validationState = validationState;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
