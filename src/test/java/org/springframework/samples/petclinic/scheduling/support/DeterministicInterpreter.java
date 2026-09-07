package org.springframework.samples.petclinic.scheduling.support;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpreter;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("test")
public class DeterministicInterpreter implements Interpreter {

	private final AtomicInteger callCount = new AtomicInteger();

	private final List<String> prompts = new java.util.concurrent.CopyOnWriteArrayList<>();

	private volatile Mode mode = Mode.SUCCESS;

	private volatile CountDownLatch blockingStarted;

	private volatile CountDownLatch blockingRelease;

	private final AtomicInteger activeCalls = new AtomicInteger();

	private final AtomicInteger maximumActiveCalls = new AtomicInteger();

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public int getCallCount() {
		return this.callCount.get();
	}

	public List<String> getPrompts() {
		return List.copyOf(this.prompts);
	}

	public void prepareBlocking(int expectedRunningCalls) {
		this.blockingStarted = new CountDownLatch(expectedRunningCalls);
		this.blockingRelease = new CountDownLatch(1);
	}

	public boolean awaitBlockingCalls(long timeout, TimeUnit unit) throws InterruptedException {
		return this.blockingStarted != null && this.blockingStarted.await(timeout, unit);
	}

	public void releaseBlockingCalls() {
		if (this.blockingRelease != null) {
			this.blockingRelease.countDown();
		}
	}

	public int getMaximumActiveCalls() {
		return this.maximumActiveCalls.get();
	}

	public void reset() {
		releaseBlockingCalls();
		this.mode = Mode.SUCCESS;
		this.blockingStarted = null;
		this.blockingRelease = null;
		this.activeCalls.set(0);
		this.maximumActiveCalls.set(0);
		this.callCount.set(0);
		this.prompts.clear();
	}

	@Override
	public CompletionStage<InterpretationResult> interpret(String prompt) {
		this.callCount.incrementAndGet();
		this.prompts.add(prompt);
		awaitReleaseWhenBlocking();
		Mode effectiveMode = this.mode;
		if (prompt != null) {
			if (prompt.contains("[interpreter:unparseable]")) {
				effectiveMode = Mode.UNPARSEABLE;
			}
			else if (prompt.contains("[interpreter:zero-window]")) {
				effectiveMode = Mode.ZERO_WINDOWS;
			}
			else if (prompt.contains("[interpreter:understood-false]")) {
				effectiveMode = Mode.UNDERSTOOD_FALSE;
			}
			else if (prompt.contains("[interpreter:transport]")) {
				effectiveMode = Mode.TRANSPORT;
			}
			else if (prompt.contains("[interpreter:never-completing]") || prompt.contains("[interpreter:restart]")
					|| prompt.contains("[interpreter:late-success]")) {
				effectiveMode = Mode.NEVER_COMPLETING;
			}
			else if (prompt.contains("[interpreter:other]")) {
				return completed(
						new InterpretationResult.ModelOutput(true, CareType.SPECIALTY, "OTHER", "exotic animal", 30,
								null, windows(), List.of(), List.of()),
						"{\"understood\":true,\"specialty\":\"OTHER\"}");
			}
		}
		return switch (effectiveMode) {
			case SUCCESS -> completed(output(true, windows()), "{\"understood\":true}");
			case UNPARSEABLE -> CompletableFuture.failedFuture(new InterpretationException(FailureKind.UNPARSEABLE,
					"not-json", new IllegalArgumentException("schema")));
			case ZERO_WINDOWS -> completed(output(true, List.of()), "{\"understood\":true,\"preferredWindows\":[]}");
			case UNDERSTOOD_FALSE -> completed(output(false, windows()), "{\"understood\":false}");
			case TRANSPORT -> CompletableFuture.failedFuture(
					new InterpretationException(FailureKind.TRANSPORT, null, new IllegalStateException("offline")));
			case NEVER_COMPLETING -> new CompletableFuture<>();
		};
	}

	private void awaitReleaseWhenBlocking() {
		CountDownLatch started = this.blockingStarted;
		CountDownLatch release = this.blockingRelease;
		if (started == null || release == null) {
			return;
		}
		int active = this.activeCalls.incrementAndGet();
		this.maximumActiveCalls.accumulateAndGet(active, Math::max);
		started.countDown();
		try {
			release.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Blocking interpreter was interrupted", exception);
		}
		finally {
			this.activeCalls.decrementAndGet();
		}
	}

	private CompletionStage<InterpretationResult> completed(InterpretationResult.ModelOutput output, String rawJson) {
		return CompletableFuture.completedFuture(new InterpretationResult(output, rawJson));
	}

	private InterpretationResult.ModelOutput output(boolean understood, List<InterpretationResult.Window> windows) {
		return new InterpretationResult.ModelOutput(understood, CareType.SPECIALTY, "surgery", null, 30, 3, windows,
				List.of(), List.of());
	}

	private List<InterpretationResult.Window> windows() {
		return List
			.of(new InterpretationResult.Window(DayOfWeek.MONDAY, null, LocalTime.of(9, 0), LocalTime.of(12, 0)));
	}

	public enum Mode {

		SUCCESS,

		UNPARSEABLE,

		ZERO_WINDOWS,

		UNDERSTOOD_FALSE,

		TRANSPORT,

		NEVER_COMPLETING

	}

}
