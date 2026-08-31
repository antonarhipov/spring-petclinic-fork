package org.springframework.samples.petclinic.shared.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommandService {

	private final CommandRepository commandRepository;

	public CommandService(CommandRepository commandRepository) {
		this.commandRepository = Objects.requireNonNull(commandRepository, "commandRepository must not be null");
	}

	public static String computeRequestHash(String input) {
		if (input == null) {
			input = "";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}

	public CommandRecord issueToken(UUID commandId, Long actorAccountId, String action, String target,
			String requestHash) {
		Objects.requireNonNull(commandId, "commandId must not be null");
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(target, "target must not be null");
		Objects.requireNonNull(requestHash, "requestHash must not be null");

		Optional<CommandRecord> existing = this.commandRepository.findByIdForUpdate(commandId);
		if (existing.isPresent()) {
			CommandRecord record = existing.get();
			validateScopeAndHash(record, action, target, requestHash);
			return record;
		}

		CommandRecord record = new CommandRecord();
		record.setId(commandId);
		record.setActorAccountId(actorAccountId);
		record.setAction(action);
		record.setTarget(target);
		record.setRequestHash(requestHash);
		record.setStatus(CommandStatus.ISSUED);
		record.setCreatedAt(Instant.now());
		return this.commandRepository.save(record);
	}

	public CommandRecord completeCommand(UUID commandId, CommandResult result) {
		Objects.requireNonNull(commandId, "commandId must not be null");
		Objects.requireNonNull(result, "result must not be null");

		CommandRecord record = this.commandRepository.findByIdForUpdate(commandId)
			.orElseThrow(() -> new IllegalStateException("Command record not found for id: " + commandId));

		record.setStatus(CommandStatus.COMPLETED);
		record.setResultStatus(result.getStatus());
		record.setResultType(result.getType());
		record.setResultId(result.getId());
		record.setResultLocation(result.getLocation());
		record.setResultBody(result.getBody());
		record.setCompletedAt(Instant.now());

		return this.commandRepository.save(record);
	}

	public CommandRecord executeOrReplay(UUID commandId, Long actorAccountId, String action, String target,
			String requestHash, Supplier<CommandResult> execution) {
		Objects.requireNonNull(commandId, "commandId must not be null");
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(target, "target must not be null");
		Objects.requireNonNull(requestHash, "requestHash must not be null");
		Objects.requireNonNull(execution, "execution must not be null");

		Optional<CommandRecord> existing = this.commandRepository.findByIdForUpdate(commandId);
		CommandRecord record;
		if (existing.isPresent()) {
			record = existing.get();
			validateScopeAndHash(record, action, target, requestHash);
			if (record.getStatus() == CommandStatus.COMPLETED) {
				// Replay cached canonical result without re-executing
				return record;
			}
		}
		else {
			record = new CommandRecord();
			record.setId(commandId);
			record.setActorAccountId(actorAccountId);
			record.setAction(action);
			record.setTarget(target);
			record.setRequestHash(requestHash);
			record.setStatus(CommandStatus.ISSUED);
			record.setCreatedAt(Instant.now());
			record = this.commandRepository.save(record);
		}

		CommandResult result = execution.get();
		record.setStatus(CommandStatus.COMPLETED);
		record.setResultStatus(result.getStatus());
		record.setResultType(result.getType());
		record.setResultId(result.getId());
		record.setResultLocation(result.getLocation());
		record.setResultBody(result.getBody());
		record.setCompletedAt(Instant.now());
		return this.commandRepository.save(record);
	}

	@Transactional(readOnly = true)
	public Optional<CommandRecord> findById(UUID commandId) {
		return this.commandRepository.findById(commandId);
	}

	private void validateScopeAndHash(CommandRecord record, String action, String target, String requestHash) {
		if (!Objects.equals(record.getAction(), action) || !Objects.equals(record.getTarget(), target)
				|| !Objects.equals(record.getRequestHash(), requestHash)) {
			throw new IllegalStateException(
					"Command token mismatch or reuse detected for command ID: " + record.getId());
		}
	}

}
