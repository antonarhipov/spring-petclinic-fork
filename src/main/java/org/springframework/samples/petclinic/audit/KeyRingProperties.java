package org.springframework.samples.petclinic.audit;

import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "petclinic.security")
public class KeyRingProperties {

	private String activeKeyId;

	private Map<String, String> keyRing = new HashMap<>();

	public String getActiveKeyId() {
		return this.activeKeyId;
	}

	public void setActiveKeyId(String activeKeyId) {
		this.activeKeyId = activeKeyId;
	}

	public Map<String, String> getKeyRing() {
		return this.keyRing;
	}

	public void setKeyRing(Map<String, String> keyRing) {
		this.keyRing = keyRing;
	}

	public byte[] getKeyBytes(String keyId) {
		if (keyId == null || !this.keyRing.containsKey(keyId)) {
			throw new IllegalArgumentException("Key ID '" + keyId + "' not found in key ring");
		}
		String keyMaterial = this.keyRing.get(keyId).trim();
		byte[] keyBytes;
		if (keyMaterial.length() == 64) {
			keyBytes = HexFormat.of().parseHex(keyMaterial);
		}
		else {
			try {
				keyBytes = Base64.getDecoder().decode(keyMaterial);
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid key format for key ID: " + keyId, e);
			}
		}
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException("Key for key ID '" + keyId
					+ "' must be exactly 256 bits (32 bytes), but was " + keyBytes.length + " bytes");
		}
		return keyBytes;
	}

	public byte[] getActiveKeyBytes() {
		if (this.activeKeyId == null || this.activeKeyId.isBlank()) {
			throw new IllegalStateException("Active key ID is not configured");
		}
		return getKeyBytes(this.activeKeyId);
	}

}
