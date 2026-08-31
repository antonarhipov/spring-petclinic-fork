package org.springframework.samples.petclinic.shared.command;

import java.io.Serializable;
import java.util.Objects;

public final class CommandResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private final int status;

	private final String type;

	private final String id;

	private final String location;

	private final String body;

	public CommandResult(int status, String type, String id, String location, String body) {
		this.status = status;
		this.type = type;
		this.id = id;
		this.location = location;
		this.body = body;
	}

	public static CommandResult ok(String type, String id, String location) {
		return new CommandResult(200, type, id, location, null);
	}

	public static CommandResult created(String type, String id, String location) {
		return new CommandResult(201, type, id, location, null);
	}

	public static CommandResult of(int status, String type, String id, String location, String body) {
		return new CommandResult(status, type, id, location, body);
	}

	public int getStatus() {
		return this.status;
	}

	public String getType() {
		return this.type;
	}

	public String getId() {
		return this.id;
	}

	public String getLocation() {
		return this.location;
	}

	public String getBody() {
		return this.body;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CommandResult that = (CommandResult) o;
		return this.status == that.status && Objects.equals(this.type, that.type) && Objects.equals(this.id, that.id)
				&& Objects.equals(this.location, that.location) && Objects.equals(this.body, that.body);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.status, this.type, this.id, this.location, this.body);
	}

	@Override
	public String toString() {
		return "CommandResult{" + "status=" + this.status + ", type='" + this.type + '\'' + ", id='" + this.id + '\''
				+ ", location='" + this.location + '\'' + '}';
	}

}
