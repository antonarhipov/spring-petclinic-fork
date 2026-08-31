package org.springframework.samples.petclinic.audit;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KeyRotationServiceTests {

	@Autowired
	private KeyRotationService keyRotationService;

	@Autowired
	private ProtectedPayloadCipher protectedPayloadCipher;

	@Autowired
	private ProtectedPayloadRepository protectedPayloadRepository;

	@Autowired
	private PayloadKeyEnvelopeRepository payloadKeyEnvelopeRepository;

	@Autowired
	private KeyRingProperties keyRingProperties;

	@Autowired
	private ProtectedPayloadService protectedPayloadService;

	@Autowired
	private KeyRotationRunRepository keyRotationRunRepository;

	@BeforeEach
	void setUp() {
		// Ensure keys are present in keyring without wiping existing keys
		this.keyRingProperties.getKeyRing()
			.put("k1", "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
		this.keyRingProperties.getKeyRing()
			.put("k2", "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f");
		this.keyRingProperties.setActiveKeyId("k1");
	}

	@Test
	void testKeyRotationRewrapsEnvelopesAndMaintainsCiphertextStability() {
		String originalText1 = "Confidential clinical notes for Rex";
		String originalText2 = "Sensitive owner scheduling prose with consent";

		ProtectedPayload payload1 = this.protectedPayloadCipher.encrypt(UUID.randomUUID(), "ClinicalNotes", 1,
				"text/plain", originalText1.getBytes(StandardCharsets.UTF_8));
		ProtectedPayload payload2 = this.protectedPayloadCipher.encrypt(UUID.randomUUID(), "OwnerProse", 1,
				"text/plain", originalText2.getBytes(StandardCharsets.UTF_8));

		ProtectedPayload saved1 = this.protectedPayloadRepository.save(payload1);
		this.payloadKeyEnvelopeRepository.save(saved1.getActiveEnvelope());
		ProtectedPayload saved2 = this.protectedPayloadRepository.save(payload2);
		this.payloadKeyEnvelopeRepository.save(saved2.getActiveEnvelope());

		byte[] originalCiphertext1 = Arrays.copyOf(saved1.getCiphertext(), saved1.getCiphertext().length);
		byte[] originalNonce1 = Arrays.copyOf(saved1.getNonce(), saved1.getNonce().length);
		String initialKeyId1 = saved1.getActiveEnvelope().getKeyId();
		assertThat(initialKeyId1).isEqualTo("k1");

		// Execute key rotation to target key "k2"
		KeyRotationRun run = this.keyRotationService.executeFullRotation("k2", 1L);

		assertThat(run.getState()).isEqualTo("COMPLETED");
		assertThat(run.getProcessedCount()).isGreaterThanOrEqualTo(2);
		assertThat(run.getFailureCount()).isEqualTo(0);
		assertThat(this.keyRingProperties.getActiveKeyId()).isEqualTo("k2");

		// Reload payload 1
		ProtectedPayload reloaded1 = this.protectedPayloadRepository.findById(saved1.getId()).orElseThrow();
		assertThat(reloaded1.getActiveEnvelope().getKeyId()).isEqualTo("k2");

		// Verify ciphertext and nonce did NOT change (Ciphertext Stability)
		assertThat(reloaded1.getCiphertext()).isEqualTo(originalCiphertext1);
		assertThat(reloaded1.getNonce()).isEqualTo(originalNonce1);

		// Verify decrypted content matches original
		byte[] decrypted1 = this.protectedPayloadCipher.decrypt(reloaded1);
		assertThat(new String(decrypted1, StandardCharsets.UTF_8)).isEqualTo(originalText1);

		byte[] decrypted2 = this.protectedPayloadCipher.decrypt(reloaded2(saved2.getId()));
		assertThat(new String(decrypted2, StandardCharsets.UTF_8)).isEqualTo(originalText2);
	}

	@Test
	void testMissingKeyInKeyringThrowsException() {
		assertThatThrownBy(() -> this.keyRotationService.startOrResumeRotation("non-existent-key", 1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Target key ID not found in keyring");
	}

	@Test
	void sourceKeyCannotBeRetiredWhileActivePayloadsReferenceIt() {
		ProtectedPayload payload = this.protectedPayloadCipher.encrypt(UUID.randomUUID(), "OwnerProse", 1, "text/plain",
				"protected".getBytes(StandardCharsets.UTF_8));
		ProtectedPayload saved = this.protectedPayloadRepository.save(payload);
		this.payloadKeyEnvelopeRepository.save(saved.getActiveEnvelope());

		assertThatThrownBy(() -> this.keyRotationService.assertKeyMayBeRetired("k1"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cannot be retired");

		this.keyRotationService.executeFullRotation("k2", 1L);
		this.keyRotationService.assertKeyMayBeRetired("k1");
	}

	@Test
	void failedRewrapDoesNotActivateTargetAndResumesFromLastSuccessfulPayload() {
		ProtectedPayload first = this.protectedPayloadService.encrypt("first payload");
		ProtectedPayload second = this.protectedPayloadService.encrypt("second payload");
		PayloadKeyEnvelope secondEnvelope = second.getActiveEnvelope();
		byte[] originalWrappedKey = Arrays.copyOf(secondEnvelope.getWrappedKey(),
				secondEnvelope.getWrappedKey().length);
		secondEnvelope.setWrappedKey(new byte[] { 1, 2, 3, 4 });
		this.payloadKeyEnvelopeRepository.saveAndFlush(secondEnvelope);

		KeyRotationRun failed = this.keyRotationService.executeFullRotation("k2", 1L);

		assertThat(failed.getState()).isEqualTo("FAILED");
		assertThat(failed.getLastPayloadId()).isEqualTo(first.getId());
		assertThat(this.keyRingProperties.getActiveKeyId()).isEqualTo("k1");

		secondEnvelope.setWrappedKey(originalWrappedKey);
		this.payloadKeyEnvelopeRepository.saveAndFlush(secondEnvelope);
		KeyRotationRun resumed = this.keyRotationService.executeFullRotation("k2", 1L);

		assertThat(resumed.getState()).isEqualTo("COMPLETED");
		assertThat(resumed.getFailureCount()).isZero();
		assertThat(this.keyRingProperties.getActiveKeyId()).isEqualTo("k2");
		byte[] decrypted = this.protectedPayloadCipher
			.decrypt(this.protectedPayloadRepository.findById(second.getId()).orElseThrow());
		assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("second payload");
	}

	@Test
	void activationIsRejectedWhenAnyPayloadStillUsesSourceEnvelope() {
		this.protectedPayloadService.encrypt("unrotated payload");
		KeyRotationRun run = new KeyRotationRun();
		run.setSourceKeyId("k1");
		run.setTargetKeyId("k2");
		run.setState("RUNNING");
		run.setLastPayloadId(Long.MAX_VALUE);
		run.setStartedAt(java.time.Instant.now());
		run = this.keyRotationRunRepository.saveAndFlush(run);

		KeyRotationRun incompleteRun = run;
		assertThatThrownBy(() -> this.keyRotationService.processBatch(incompleteRun, 100))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("incomplete");
		assertThat(this.keyRingProperties.getActiveKeyId()).isEqualTo("k1");
	}

	private ProtectedPayload reloaded2(Long id) {
		return this.protectedPayloadRepository.findById(id).orElseThrow();
	}

}
