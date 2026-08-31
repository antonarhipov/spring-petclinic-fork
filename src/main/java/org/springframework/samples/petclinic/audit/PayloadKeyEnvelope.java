package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payload_key_envelopes")
public class PayloadKeyEnvelope {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payload_id", nullable = false)
	private ProtectedPayload payload;

	@Column(name = "key_id", nullable = false, length = 100)
	private String keyId;

	@Column(name = "wrapping_algorithm", nullable = false, length = 32)
	private String wrappingAlgorithm = "AES-256-GCM";

	@Column(name = "wrapping_nonce", nullable = false, length = 12)
	private byte[] wrappingNonce;

	@Lob
	@Column(name = "wrapped_key", nullable = false)
	private byte[] wrappedKey;

	@Column(name = "rotation_run_id")
	private Long rotationRunId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public ProtectedPayload getPayload() {
		return this.payload;
	}

	public void setPayload(ProtectedPayload payload) {
		this.payload = payload;
	}

	public String getKeyId() {
		return this.keyId;
	}

	public void setKeyId(String keyId) {
		this.keyId = keyId;
	}

	public String getWrappingAlgorithm() {
		return this.wrappingAlgorithm;
	}

	public void setWrappingAlgorithm(String wrappingAlgorithm) {
		this.wrappingAlgorithm = wrappingAlgorithm;
	}

	public byte[] getWrappingNonce() {
		return this.wrappingNonce;
	}

	public void setWrappingNonce(byte[] wrappingNonce) {
		this.wrappingNonce = wrappingNonce;
	}

	public byte[] getWrappedKey() {
		return this.wrappedKey;
	}

	public void setWrappedKey(byte[] wrappedKey) {
		this.wrappedKey = wrappedKey;
	}

	public Long getRotationRunId() {
		return this.rotationRunId;
	}

	public void setRotationRunId(Long rotationRunId) {
		this.rotationRunId = rotationRunId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
