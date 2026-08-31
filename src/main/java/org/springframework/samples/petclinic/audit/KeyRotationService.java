package org.springframework.samples.petclinic.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KeyRotationService {

	private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int GCM_TAG_LENGTH_BITS = 128;

	private static final int NONCE_LENGTH_BYTES = 12;

	private final KeyRotationRunRepository keyRotationRunRepository;

	private final ProtectedPayloadRepository protectedPayloadRepository;

	private final PayloadKeyEnvelopeRepository payloadKeyEnvelopeRepository;

	private final KeyRingProperties keyRingProperties;

	private final AuditService auditService;

	private final SecureRandom secureRandom = new SecureRandom();

	public KeyRotationService(KeyRotationRunRepository keyRotationRunRepository,
			ProtectedPayloadRepository protectedPayloadRepository,
			PayloadKeyEnvelopeRepository payloadKeyEnvelopeRepository, KeyRingProperties keyRingProperties,
			AuditService auditService) {
		this.keyRotationRunRepository = Objects.requireNonNull(keyRotationRunRepository,
				"keyRotationRunRepository must not be null");
		this.protectedPayloadRepository = Objects.requireNonNull(protectedPayloadRepository,
				"protectedPayloadRepository must not be null");
		this.payloadKeyEnvelopeRepository = Objects.requireNonNull(payloadKeyEnvelopeRepository,
				"payloadKeyEnvelopeRepository must not be null");
		this.keyRingProperties = Objects.requireNonNull(keyRingProperties, "keyRingProperties must not be null");
		this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
	}

	public KeyRotationRun startOrResumeRotation(String targetKeyId, Long actorAccountId) {
		Objects.requireNonNull(targetKeyId, "targetKeyId must not be null");
		if (!this.keyRingProperties.getKeyRing().containsKey(targetKeyId)) {
			throw new IllegalArgumentException("Target key ID not found in keyring: " + targetKeyId);
		}

		Optional<KeyRotationRun> existingRun = this.keyRotationRunRepository
			.findFirstByTargetKeyIdAndStateInOrderByCreatedAtDesc(targetKeyId, List.of("PENDING", "RUNNING"));

		if (existingRun.isPresent()) {
			KeyRotationRun run = existingRun.get();
			run.setState("RUNNING");
			return this.keyRotationRunRepository.save(run);
		}

		KeyRotationRun newRun = new KeyRotationRun();
		newRun.setSourceKeyId(this.keyRingProperties.getActiveKeyId());
		newRun.setTargetKeyId(targetKeyId);
		newRun.setState("RUNNING");
		newRun.setStartedAt(Instant.now());
		newRun.setProcessedCount(0);
		newRun.setFailureCount(0);
		newRun.setLastPayloadId(0L);

		return this.keyRotationRunRepository.save(newRun);
	}

	public int processBatch(KeyRotationRun run, int batchSize) {
		Objects.requireNonNull(run, "run must not be null");
		Long lastId = run.getLastPayloadId() != null ? run.getLastPayloadId() : 0L;

		List<ProtectedPayload> payloads = this.protectedPayloadRepository.findByIdGreaterThanOrderByIdAsc(lastId,
				PageRequest.of(0, batchSize));

		if (payloads.isEmpty()) {
			// All items processed - mark run completed and activate target key
			this.keyRingProperties.setActiveKeyId(run.getTargetKeyId());
			run.setState("COMPLETED");
			run.setCompletedAt(Instant.now());
			this.keyRotationRunRepository.save(run);

			this.auditService.recordEvent(null, "KEY_ROTATION_COMPLETED", "KeyRotationRun", run.getId().toString(),
					"SUCCESS", UUID.randomUUID(), null, null);
			return 0;
		}

		byte[] targetKek = this.keyRingProperties.getKeyBytes(run.getTargetKeyId());

		for (ProtectedPayload payload : payloads) {
			try {
				rewrapPayloadToTargetKey(payload, run, targetKek);
				run.setProcessedCount(run.getProcessedCount() + 1);
			}
			catch (Exception e) {
				run.setFailureCount(run.getFailureCount() + 1);
				run.setFailureCategory("REWRAP_ERROR: " + e.getMessage());
			}
			run.setLastPayloadId(payload.getId());
		}

		this.keyRotationRunRepository.save(run);
		return payloads.size();
	}

	public KeyRotationRun executeFullRotation(String targetKeyId, Long actorAccountId) {
		KeyRotationRun run = startOrResumeRotation(targetKeyId, actorAccountId);
		int batchSize = 100;
		while ("RUNNING".equals(run.getState())) {
			int processed = processBatch(run, batchSize);
			if (processed == 0) {
				break;
			}
		}
		return run;
	}

	private void rewrapPayloadToTargetKey(ProtectedPayload payload, KeyRotationRun run, byte[] targetKek) {
		Optional<PayloadKeyEnvelope> existingTargetEnvelope = this.payloadKeyEnvelopeRepository
			.findByPayloadIdAndKeyId(payload.getId(), run.getTargetKeyId());

		if (existingTargetEnvelope.isPresent()) {
			payload.setActiveEnvelope(existingTargetEnvelope.get());
			this.protectedPayloadRepository.save(payload);
			return;
		}

		PayloadKeyEnvelope currentEnvelope = payload.getActiveEnvelope();
		if (currentEnvelope == null) {
			throw new IllegalStateException("Payload " + payload.getId() + " has no active envelope");
		}

		byte[] sourceKek = this.keyRingProperties.getKeyBytes(currentEnvelope.getKeyId());
		byte[] wrappingAad = payload.getArtifactUuid().toString().getBytes(StandardCharsets.UTF_8);

		byte[] dek;
		try {
			Cipher unwrapCipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			unwrapCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sourceKek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, currentEnvelope.getWrappingNonce()));
			unwrapCipher.updateAAD(wrappingAad);
			dek = unwrapCipher.doFinal(currentEnvelope.getWrappedKey());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to unwrap DEK for payload " + payload.getId(), e);
		}

		byte[] newWrappingNonce = new byte[NONCE_LENGTH_BYTES];
		this.secureRandom.nextBytes(newWrappingNonce);

		byte[] newWrappedKey;
		try {
			Cipher wrapCipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			wrapCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(targetKek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, newWrappingNonce));
			wrapCipher.updateAAD(wrappingAad);
			newWrappedKey = wrapCipher.doFinal(dek);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to rewrap DEK for payload " + payload.getId(), e);
		}

		PayloadKeyEnvelope newEnvelope = new PayloadKeyEnvelope();
		newEnvelope.setPayload(payload);
		newEnvelope.setKeyId(run.getTargetKeyId());
		newEnvelope.setWrappingAlgorithm("AES-256-GCM");
		newEnvelope.setWrappingNonce(newWrappingNonce);
		newEnvelope.setWrappedKey(newWrappedKey);
		newEnvelope.setRotationRunId(run.getId());

		PayloadKeyEnvelope savedEnvelope = this.payloadKeyEnvelopeRepository.save(newEnvelope);
		payload.setActiveEnvelope(savedEnvelope);
		this.protectedPayloadRepository.save(payload);
	}

}
