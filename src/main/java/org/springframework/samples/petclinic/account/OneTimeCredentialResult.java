package org.springframework.samples.petclinic.account;

import java.time.Instant;

public record OneTimeCredentialResult(String username, String oneTimePassword, Instant expiresAt) {
}
