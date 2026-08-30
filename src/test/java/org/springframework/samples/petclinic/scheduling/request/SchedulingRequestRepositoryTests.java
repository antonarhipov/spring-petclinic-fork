package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.AccountRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SchedulingRequestRepositoryTests {

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	ActivePetRequestRepository activePets;

	@Autowired
	RequestTextRevisionRepository texts;

	@Autowired
	ConsentRecordRepository consents;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void requestTextAndConsentArePersisted() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.requests.saveAndFlush(request);
		RequestTextRevision text = new RequestTextRevision();
		text.setRequestId(request.getId());
		text.setSequence(1);
		text.setSourceText("Need a wellness exam next week");
		text.setSourceHash("abc");
		text.setSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
		text.setClinicZoneId("America/Chicago");
		text.setEmergencyScreenVersion("none");
		this.texts.saveAndFlush(text);
		Account account = new Account();
		account.setUsername("repo-owner");
		account.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		account.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		account.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.accounts.saveAndFlush(account);
		ConsentRecord consent = new ConsentRecord();
		consent.setTextRevisionId(text.getId());
		consent.setDecision("AGREE");
		consent.setActorAccountId(account.getId());
		consent.setDataUseCopyVersion("v1");
		consent.setDecidedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.consents.saveAndFlush(consent);
		assertThat(this.texts.findByRequestIdOrderBySequenceAsc(request.getId())).hasSize(1);
		assertThat(this.consents.findFirstByTextRevisionIdOrderByDecidedAtDesc(text.getId())).isPresent();
	}

	@Test
	void concurrentActivePetGuardIsUnique() {
		SchedulingRequest first = persistRequest(1);
		ActivePetRequest guard = new ActivePetRequest();
		guard.setPetId(1);
		guard.setRequestId(first.getId());
		this.activePets.saveAndFlush(guard);
		SchedulingRequest second = persistRequest(1);
		assertThatThrownBy(() -> this.jdbc.update("insert into active_pet_requests (pet_id, request_id) values (?, ?)",
				1, second.getId()))
			.isInstanceOf(Exception.class);
	}

	private SchedulingRequest persistRequest(int petId) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(petId);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		return this.requests.saveAndFlush(request);
	}

}
