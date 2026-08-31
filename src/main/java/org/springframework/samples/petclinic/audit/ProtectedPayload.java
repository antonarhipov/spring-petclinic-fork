package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.UUID;
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
@Table(name = "protected_payloads")
public class ProtectedPayload {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "artifact_uuid", nullable = false, unique = true)
	private UUID artifactUuid;

	@Column(name = "artifact_type", nullable = false, length = 80)
	private String artifactType;

	@Column(name = "schema_version", nullable = false)
	private Integer schemaVersion;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "algorithm", nullable = false, length = 32)
	private String algorithm = "AES-256-GCM";

	@Column(name = "nonce", nullable = false, length = 12)
	private byte[] nonce;

	@Lob
	@Column(name = "ciphertext", nullable = false)
	private byte[] ciphertext;

	@Lob
	@Column(name = "authenticated_metadata", nullable = false)
	private byte[] authenticatedMetadata;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "active_envelope_id")
	private PayloadKeyEnvelope activeEnvelope;

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

	public UUID getArtifactUuid() {
		return this.artifactUuid;
	}

	public void setArtifactUuid(UUID artifactUuid) {
		this.artifactUuid = artifactUuid;
	}

	public String getArtifactType() {
		return this.artifactType;
	}

	public void setArtifactType(String artifactType) {
		this.artifactType = artifactType;
	}

	public Integer getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(Integer schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getContentType() {
		return this.contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getAlgorithm() {
		return this.algorithm;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public byte[] getNonce() {
		return this.nonce;
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}

	public byte[] getCiphertext() {
		return this.ciphertext;
	}

	public void setCiphertext(byte[] ciphertext) {
		this.ciphertext = ciphertext;
	}

	public byte[] getAuthenticatedMetadata() {
		return this.authenticatedMetadata;
	}

	public void setAuthenticatedMetadata(byte[] authenticatedMetadata) {
		this.authenticatedMetadata = authenticatedMetadata;
	}

	public PayloadKeyEnvelope getActiveEnvelope() {
		return this.activeEnvelope;
	}

	public void setActiveEnvelope(PayloadKeyEnvelope activeEnvelope) {
		this.activeEnvelope = activeEnvelope;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
