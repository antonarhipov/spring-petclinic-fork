package org.springframework.samples.petclinic.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectedPayloadServiceTests {

	private final ProtectedPayloadRepository payloadRepository = mock(ProtectedPayloadRepository.class);

	private final PayloadKeyEnvelopeRepository envelopeRepository = mock(PayloadKeyEnvelopeRepository.class);

	private final ProtectedPayloadCipher cipher = mock(ProtectedPayloadCipher.class);

	private final ProtectedPayloadService service = new ProtectedPayloadService(this.payloadRepository,
			this.envelopeRepository, this.cipher);

	@Test
	void optionalTextDecryptionReturnsEmptyWhenHistoricalKeyIsUnavailable() {
		ProtectedPayload payload = new ProtectedPayload();
		when(this.cipher.decrypt(payload)).thenThrow(new MissingProtectedPayloadKeyException("retired-key"));

		assertThat(this.service.decryptToStringIfKeyAvailable(payload)).isEmpty();
	}

	@Test
	void optionalTextDecryptionStillFailsClosedForTampering() {
		ProtectedPayload payload = new ProtectedPayload();
		when(this.cipher.decrypt(payload)).thenThrow(new SecurityException("Authentication failed"));

		assertThatThrownBy(() -> this.service.decryptToStringIfKeyAvailable(payload))
			.isInstanceOf(SecurityException.class);
	}

}
