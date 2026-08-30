package org.springframework.samples.petclinic.account;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.audit.AuditEvent;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAuditTests {

	@Test
	void accountAuditOmitsCredentialsHashesSessionIdsAndUsernameExistence() {
		AuditService auditService = mock(AuditService.class);
		when(auditService.record(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			AuditEvent event = new AuditEvent();
			event.setAction(invocation.getArgument(2));
			event.setBeforeJson(invocation.getArgument(6));
			event.setAfterJson(invocation.getArgument(7));
			return event;
		});
		AccountAuditService audits = new AccountAuditService(auditService);
		Account account = new Account();
		account.setUsername("betty");
		account.setPasswordHash("$2a$10$not-a-real-hash-but-looks-like-one");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(11);
		account.setMustChangePassword(true);
		account.setCredentialVersion(3);
		ReflectionTestUtils.setField(account, "id", 44L);

		AuditEvent provisioned = audits.recordProvision(99L, account);
		AuditEvent reset = audits.recordReset(99L, account);
		AuditEvent changed = audits.recordPasswordChange(account);

		assertRedacted(provisioned);
		assertRedacted(reset);
		assertRedacted(changed);
		verify(auditService).record(eq("STAFF"), eq(99L), eq("ACCOUNT_PROVISIONED"), eq("ACCOUNT"), eq("44"), isNull(),
				isNull(), any());
		verify(auditService).record(eq("STAFF"), eq(99L), eq("ACCOUNT_RESET"), eq("ACCOUNT"), eq("44"), isNull(),
				isNull(), any());
		verify(auditService).record(eq("OWNER"), eq(44L), eq("PASSWORD_CHANGED"), eq("ACCOUNT"), eq("44"), isNull(),
				isNull(), any());
	}

	private static void assertRedacted(AuditEvent event) {
		String combined = String.valueOf(event.getBeforeJson()) + event.getAfterJson();
		assertThat(combined).doesNotContain("betty");
		assertThat(combined).doesNotContain("$2a$");
		assertThat(combined).doesNotContain("password");
		assertThat(combined).doesNotContain("session");
		assertThat(combined.toLowerCase()).doesNotContain("exists");
	}

}
