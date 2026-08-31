package org.springframework.samples.petclinic.shared.command;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
class CommandServiceTests {

	@Autowired
	private CommandService commandService;

	@Autowired
	private CommandRepository commandRepository;

	private UUID commandId;

	private String action;

	private String target;

	private String requestHash;

	@BeforeEach
	void setUp() {
		this.commandId = UUID.randomUUID();
		this.action = "BOOK_APPOINTMENT";
		this.target = "pet:1";
		this.requestHash = CommandService.computeRequestHash("{\"petId\":1,\"slot\":\"2026-09-01T10:00\"}");
	}

	@Test
	void testComputeRequestHashIsDeterministic() {
		String hash1 = CommandService.computeRequestHash("test payload");
		String hash2 = CommandService.computeRequestHash("test payload");
		assertThat(hash1).isEqualTo(hash2);
		assertThat(hash1).hasSize(64); // SHA-256 in hex
	}

	@Test
	void testIssueTokenCreatesIssuedRecord() {
		CommandRecord record = this.commandService.issueToken(this.commandId, 1L, this.action, this.target,
				this.requestHash);
		assertThat(record).isNotNull();
		assertThat(record.getId()).isEqualTo(this.commandId);
		assertThat(record.getStatus()).isEqualTo(CommandStatus.ISSUED);
		assertThat(record.getActorAccountId()).isEqualTo(1L);
		assertThat(record.getRequestHash()).isEqualTo(this.requestHash);
	}

	@Test
	void testIssueTokenReusesSameRecordOnMatchingParameters() {
		CommandRecord first = this.commandService.issueToken(this.commandId, 1L, this.action, this.target,
				this.requestHash);
		CommandRecord second = this.commandService.issueToken(this.commandId, 1L, this.action, this.target,
				this.requestHash);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getStatus()).isEqualTo(CommandStatus.ISSUED);
	}

	@Test
	void testIssueTokenRejectsHashMismatch() {
		this.commandService.issueToken(this.commandId, 1L, this.action, this.target, this.requestHash);

		String differentHash = CommandService.computeRequestHash("{\"different\":true}");
		assertThatThrownBy(
				() -> this.commandService.issueToken(this.commandId, 1L, this.action, this.target, differentHash))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mismatch");
	}

	@Test
	void testIssueTokenRejectsActionMismatch() {
		this.commandService.issueToken(this.commandId, 1L, this.action, this.target, this.requestHash);

		assertThatThrownBy(
				() -> this.commandService.issueToken(this.commandId, 1L, "OTHER_ACTION", this.target, this.requestHash))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mismatch");
	}

	@Test
	void testIssueTokenRejectsActorMismatch() {
		this.commandService.issueToken(this.commandId, 1L, this.action, this.target, this.requestHash);

		assertThatThrownBy(
				() -> this.commandService.issueToken(this.commandId, 2L, this.action, this.target, this.requestHash))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mismatch");
	}

	@Test
	void testExecuteOrReplayExecutesOnceAndReplays() {
		AtomicInteger executionCount = new AtomicInteger(0);

		CommandResult expectedResult = CommandResult.of(200, "Appointment", "101", "/appointments/101", "{\"id\":101}");

		// First execution
		CommandRecord record1 = this.commandService.executeOrReplay(this.commandId, 1L, this.action, this.target,
				this.requestHash, () -> {
					executionCount.incrementAndGet();
					return expectedResult;
				});

		assertThat(record1.getStatus()).isEqualTo(CommandStatus.COMPLETED);
		assertThat(record1.getResultId()).isEqualTo("101");
		assertThat(record1.getResultLocation()).isEqualTo("/appointments/101");
		assertThat(executionCount.get()).isEqualTo(1);

		// Replay call with same token
		CommandRecord record2 = this.commandService.executeOrReplay(this.commandId, 1L, this.action, this.target,
				this.requestHash, () -> {
					executionCount.incrementAndGet();
					return CommandResult.ok("Appointment", "999", "/appointments/999");
				});

		assertThat(record2.getStatus()).isEqualTo(CommandStatus.COMPLETED);
		assertThat(record2.getResultId()).isEqualTo("101"); // Preserves first canonical
															// result
		assertThat(executionCount.get()).isEqualTo(1); // Lambda was NOT executed second
														// time
	}

	@Test
	void testCompleteCommandUpdatesStatusAndMetadata() {
		this.commandService.issueToken(this.commandId, 1L, this.action, this.target, this.requestHash);

		CommandResult result = CommandResult.of(201, "Offer", "202", "/offers/202", "{\"offerId\":202}");
		CommandRecord completed = this.commandService.completeCommand(this.commandId, result);

		assertThat(completed.getStatus()).isEqualTo(CommandStatus.COMPLETED);
		assertThat(completed.getResultStatus()).isEqualTo(201);
		assertThat(completed.getResultType()).isEqualTo("Offer");
		assertThat(completed.getResultId()).isEqualTo("202");
		assertThat(completed.getCompletedAt()).isNotNull();
	}

}
