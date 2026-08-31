package org.springframework.samples.petclinic.audit;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProtectedPayloadService {

	private final ProtectedPayloadRepository payloadRepository;

	private final PayloadKeyEnvelopeRepository envelopeRepository;

	private final ProtectedPayloadCipher cipher;

	public ProtectedPayloadService(ProtectedPayloadRepository payloadRepository,
			PayloadKeyEnvelopeRepository envelopeRepository, ProtectedPayloadCipher cipher) {
		this.payloadRepository = Objects.requireNonNull(payloadRepository, "payloadRepository must not be null");
		this.envelopeRepository = Objects.requireNonNull(envelopeRepository, "envelopeRepository must not be null");
		this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
	}

	public ProtectedPayload store(UUID artifactUuid, String artifactType, int schemaVersion, String contentType,
			byte[] cleartext) {
		ProtectedPayload payload = this.cipher.encrypt(artifactUuid, artifactType, schemaVersion, contentType,
				cleartext);
		PayloadKeyEnvelope envelope = payload.getActiveEnvelope();
		payload.setActiveEnvelope(null);

		ProtectedPayload savedPayload = this.payloadRepository.save(payload);
		envelope.setPayload(savedPayload);
		PayloadKeyEnvelope savedEnvelope = this.envelopeRepository.save(envelope);

		savedPayload.setActiveEnvelope(savedEnvelope);
		return this.payloadRepository.save(savedPayload);
	}

	public ProtectedPayload store(UUID artifactUuid, String artifactType, int schemaVersion, String contentType,
			String cleartextUtf8) {
		Objects.requireNonNull(cleartextUtf8, "cleartextUtf8 must not be null");
		return store(artifactUuid, artifactType, schemaVersion, contentType,
				cleartextUtf8.getBytes(StandardCharsets.UTF_8));
	}

	public ProtectedPayload encrypt(String cleartextUtf8) {
		Objects.requireNonNull(cleartextUtf8, "cleartextUtf8 must not be null");
		return store(UUID.randomUUID(), "GENERAL", 1, "text/plain", cleartextUtf8);
	}

	public ProtectedPayload storeJson(String artifactType, String jsonUtf8) {
		Objects.requireNonNull(jsonUtf8, "jsonUtf8 must not be null");
		return store(UUID.randomUUID(), artifactType, 1, "application/json", jsonUtf8);
	}

	@Transactional(readOnly = true)
	public byte[] decrypt(ProtectedPayload payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		return this.cipher.decrypt(payload);
	}

	@Transactional(readOnly = true)
	public String decryptToString(ProtectedPayload payload) {
		return new String(decrypt(payload), StandardCharsets.UTF_8);
	}

	/**
	 * Decrypts protected text when its referenced key is available. A missing historical
	 * key is represented as an empty result so read-side projections can remain usable
	 * without exposing ciphertext. Authentication and tampering failures continue to
	 * propagate.
	 */
	@Transactional(readOnly = true)
	public Optional<String> decryptToStringIfKeyAvailable(ProtectedPayload payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		try {
			return Optional.of(decryptToString(payload));
		}
		catch (MissingProtectedPayloadKeyException ex) {
			return Optional.empty();
		}
	}

	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public <T> T decrypt(ProtectedPayload payload, Class<T> clazz) {
		if (payload == null) {
			return null;
		}
		if (clazz.equals(String.class)) {
			return (T) decryptToString(payload);
		}
		if (clazz.equals(byte[].class)) {
			return (T) decrypt(payload);
		}
		throw new IllegalArgumentException("Unsupported decryption target type: " + clazz);
	}

	@Transactional(readOnly = true)
	public Optional<ProtectedPayload> findByArtifactUuid(UUID artifactUuid) {
		return this.payloadRepository.findByArtifactUuid(artifactUuid);
	}

}
