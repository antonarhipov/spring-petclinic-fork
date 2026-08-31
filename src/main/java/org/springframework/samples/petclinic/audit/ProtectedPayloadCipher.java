package org.springframework.samples.petclinic.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM envelope cipher providing authenticated encryption, fail-closed tampering
 * detection, and key wrapping with rotation support.
 */
@Component
public class ProtectedPayloadCipher {

	public static final String ALGORITHM = "AES-256-GCM";

	private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int GCM_TAG_LENGTH_BITS = 128;

	private static final int NONCE_LENGTH_BYTES = 12;

	private static final int KEY_LENGTH_BYTES = 32;

	private final KeyRingProperties keyRingProperties;

	private final SecureRandom secureRandom = new SecureRandom();

	public ProtectedPayloadCipher(KeyRingProperties keyRingProperties) {
		this.keyRingProperties = Objects.requireNonNull(keyRingProperties, "keyRingProperties must not be null");
	}

	public ProtectedPayload encrypt(UUID artifactUuid, String artifactType, int schemaVersion, String contentType,
			byte[] plaintext) {
		Objects.requireNonNull(artifactUuid, "artifactUuid must not be null");
		Objects.requireNonNull(artifactType, "artifactType must not be null");
		Objects.requireNonNull(contentType, "contentType must not be null");
		Objects.requireNonNull(plaintext, "plaintext must not be null");

		String activeKeyId = this.keyRingProperties.getActiveKeyId();
		byte[] kek = this.keyRingProperties.getActiveKeyBytes();

		byte[] dek = new byte[KEY_LENGTH_BYTES];
		this.secureRandom.nextBytes(dek);

		byte[] payloadNonce = new byte[NONCE_LENGTH_BYTES];
		this.secureRandom.nextBytes(payloadNonce);

		byte[] metadataBytes = buildAuthenticatedMetadata(artifactUuid, artifactType, schemaVersion, contentType);

		byte[] ciphertext;
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, payloadNonce));
			cipher.updateAAD(metadataBytes);
			ciphertext = cipher.doFinal(plaintext);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to encrypt payload data", e);
		}

		byte[] wrappingNonce = new byte[NONCE_LENGTH_BYTES];
		this.secureRandom.nextBytes(wrappingNonce);

		byte[] wrappedKey;
		byte[] wrappingAad = artifactUuid.toString().getBytes(StandardCharsets.UTF_8);
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, wrappingNonce));
			cipher.updateAAD(wrappingAad);
			wrappedKey = cipher.doFinal(dek);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to wrap data encryption key", e);
		}

		ProtectedPayload payload = new ProtectedPayload();
		payload.setArtifactUuid(artifactUuid);
		payload.setArtifactType(artifactType);
		payload.setSchemaVersion(schemaVersion);
		payload.setContentType(contentType);
		payload.setAlgorithm(ALGORITHM);
		payload.setNonce(payloadNonce);
		payload.setCiphertext(ciphertext);
		payload.setAuthenticatedMetadata(metadataBytes);

		PayloadKeyEnvelope envelope = new PayloadKeyEnvelope();
		envelope.setPayload(payload);
		envelope.setKeyId(activeKeyId);
		envelope.setWrappingAlgorithm(ALGORITHM);
		envelope.setWrappingNonce(wrappingNonce);
		envelope.setWrappedKey(wrappedKey);

		payload.setActiveEnvelope(envelope);
		return payload;
	}

	public byte[] decrypt(ProtectedPayload payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		PayloadKeyEnvelope envelope = payload.getActiveEnvelope();
		if (envelope == null) {
			throw new IllegalStateException("Protected payload has no active envelope");
		}

		byte[] kek = this.keyRingProperties.getKeyBytes(envelope.getKeyId());
		byte[] wrappingAad = payload.getArtifactUuid().toString().getBytes(StandardCharsets.UTF_8);

		byte[] dek;
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.getWrappingNonce()));
			cipher.updateAAD(wrappingAad);
			dek = cipher.doFinal(envelope.getWrappedKey());
		}
		catch (GeneralSecurityException e) {
			throw new SecurityException("Tampering or decryption failure while unwrapping payload data key", e);
		}

		try {
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.getNonce()));
			cipher.updateAAD(payload.getAuthenticatedMetadata());
			return cipher.doFinal(payload.getCiphertext());
		}
		catch (GeneralSecurityException e) {
			throw new SecurityException("Tampering or decryption failure while decrypting protected payload", e);
		}
	}

	public static byte[] buildAuthenticatedMetadata(UUID artifactUuid, String artifactType, int schemaVersion,
			String contentType) {
		String metadata = artifactUuid + ":" + artifactType + ":" + schemaVersion + ":" + contentType + ":" + ALGORITHM;
		return metadata.getBytes(StandardCharsets.UTF_8);
	}

}
