package org.springframework.samples.petclinic.audit;

/**
 * Raised when a protected payload references a key that is not present in the configured
 * key ring.
 */
public class MissingProtectedPayloadKeyException extends IllegalStateException {

	private final String keyId;

	public MissingProtectedPayloadKeyException(String keyId) {
		super("Key ID '" + keyId + "' not found in key ring");
		this.keyId = keyId;
	}

	public String getKeyId() {
		return this.keyId;
	}

}
