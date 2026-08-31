package org.springframework.samples.petclinic.audit;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedPayloadCipherTests {

	private KeyRingProperties keyRingProperties;

	private ProtectedPayloadCipher cipher;

	@BeforeEach
	void setUp() {
		this.keyRingProperties = new KeyRingProperties();
		this.keyRingProperties.setActiveKeyId("k2026-01");
		this.keyRingProperties
			.setKeyRing(Map.of("k2026-01", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
					"k2025-01", "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"));
		this.cipher = new ProtectedPayloadCipher(this.keyRingProperties);
	}

	@Test
	void testEncryptAndDecryptSuccess() {
		UUID artifactUuid = UUID.randomUUID();
		String originalText = "My pet Max has been coughing for 2 days";
		byte[] plaintext = originalText.getBytes(StandardCharsets.UTF_8);

		ProtectedPayload payload = this.cipher.encrypt(artifactUuid, "VISIT_REASON", 1, "text/plain", plaintext);

		assertThat(payload).isNotNull();
		assertThat(payload.getArtifactUuid()).isEqualTo(artifactUuid);
		assertThat(payload.getCiphertext()).isNotEqualTo(plaintext);
		assertThat(payload.getActiveEnvelope()).isNotNull();
		assertThat(payload.getActiveEnvelope().getKeyId()).isEqualTo("k2026-01");

		byte[] decrypted = this.cipher.decrypt(payload);
		assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(originalText);
	}

	@Test
	void testTamperedCiphertextFailsClosed() {
		UUID artifactUuid = UUID.randomUUID();
		ProtectedPayload payload = this.cipher.encrypt(artifactUuid, "CLINICAL_NOTE", 1, "text/plain",
				"Confidential treatment note".getBytes(StandardCharsets.UTF_8));

		// Tamper with ciphertext
		byte[] corrupted = payload.getCiphertext().clone();
		corrupted[0] ^= 0xFF;
		payload.setCiphertext(corrupted);

		assertThatThrownBy(() -> this.cipher.decrypt(payload)).isInstanceOf(SecurityException.class);
	}

	@Test
	void testMissingKeyFailsClosed() {
		UUID artifactUuid = UUID.randomUUID();
		ProtectedPayload payload = this.cipher.encrypt(artifactUuid, "CLINICAL_NOTE", 1, "text/plain",
				"Confidential".getBytes(StandardCharsets.UTF_8));

		// Set non-existent key ID
		payload.getActiveEnvelope().setKeyId("unknown-key-id");

		assertThatThrownBy(() -> this.cipher.decrypt(payload)).isInstanceOf(IllegalArgumentException.class);
	}

}
