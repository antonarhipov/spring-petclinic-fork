---
name: spring-ai-ollama-integration
description: Implement or review Spring AI 2.x Ollama chat integration in Spring Boot 4 applications. Use for the Ollama starter, configuration-driven model selection, 2.x property names and options API, ChatClient wiring, reliable connect and read timeouts, typed structured output, validation, and focused tests; do not use for direct Ollama HTTP clients or non-Ollama providers.
---

# Spring AI Ollama Integration

Use Spring AI's Ollama starter and auto-configuration. Keep provider selection, transport behavior, and model output explicit and testable.

This skill targets Spring AI 2.x. Several 1.x idioms still compile or bind with a deprecation warning, so they slip through unnoticed; treat every rule in [Follow Spring AI 2.x Conventions](#follow-spring-ai-2x-conventions) as mandatory and do not fall back on memorized 1.x patterns.

## Configure the Dependency and Model

Import a Spring AI BOM compatible with the application's Spring Boot version and add `org.springframework.ai:spring-ai-starter-model-ollama`. Keep Maven and Gradle aligned when a repository supports both.

Select the runtime model in `application.properties`; never hardcode it in Java:

```properties
spring.ai.ollama.base-url=${SPRING_AI_OLLAMA_BASE_URL:http://localhost:11434}
spring.ai.ollama.chat.model=${SPRING_AI_OLLAMA_CHAT_MODEL:ministral-3:14b}
```

The fallback model tag is an example and must name a model installed in the target Ollama instance. The environment variable allows deployment-specific selection without rebuilding the application.

Never write `spring.ai.ollama.chat.options.model` or any other `spring.ai.<provider>.<type>.options.*` key. The `.options` segment was removed in Spring AI 2.0; the old keys survive only as deprecated aliases, so the application still starts and the mistake is easy to miss. Do not introduce a custom property (for example `myapp.ai.ollama.model`) as a substitute for the Spring AI key either.

Add generation options such as `spring.ai.ollama.chat.num-predict`, `spring.ai.ollama.chat.think`, or `spring.ai.ollama.chat.keep-alive` only when the use case needs them. These are model options, not HTTP transport timeouts. Note that `think-option` was renamed to `think` in 2.0, and Spring AI no longer applies a default temperature: Ollama's native default is used unless `spring.ai.ollama.chat.temperature` is set explicitly.

## Configure the Client and Timeouts

Build the application `ChatClient` from the auto-configured `ChatClient.Builder`:

```java
@Bean
ChatClient ollamaChatClient(ChatClient.Builder builder) {
	return builder.build();
}
```

The injected builder already wraps the auto-configured `OllamaChatModel`, whose default options are bound from `spring.ai.ollama.chat.*`. `builder.build()` therefore preserves every value from `application.properties`; no extra call is needed to keep them. Do not construct `ChatClient.builder(chatModel)` inline in services, and do not create a second `ChatModel` by hand.

Do not rely on server, MVC, or generation-option properties to bound an Ollama HTTP call. Read and apply [references/ollama-timeouts.md](references/ollama-timeouts.md) whenever implementing or changing transport timeouts. It contains the proven Spring Boot 4 `HttpClientSettings` plus JDK request-factory configuration and its verification test.

The timeout customizer is a Boot-level `RestClientCustomizer`. Check whether the application has other auto-configured `RestClient` consumers: the same request factory may apply to them. Preserve the working pattern unless the target Spring AI version offers a verified, narrower customization seam.

## Follow Spring AI 2.x Conventions

These are the 2.0 changes most likely to be violated by code written from 1.x habits. Check each one when writing or reviewing Ollama integration code.

- **Flattened properties.** Use `spring.ai.ollama.chat.model`, `spring.ai.ollama.chat.temperature`, `spring.ai.ollama.chat.num-predict`, and so on. Never use the `.options.` segment (see above).
- **Options take a builder on `ChatClient`.** `.options(...)` and `.defaultOptions(...)` accept a `ChatOptions.Builder` subtype, not a built instance. The builder is merged with the model's default options before the first advisor runs, so only the fields you set override the property-driven defaults:

  ```java
  chatClient.prompt()
  	.options(OllamaChatOptions.builder().numPredict(512)) // model still comes from properties
  	.user(userInput)
  	.call()
  	.content();
  ```

  This merge happens only in `ChatClient`. `ChatModel.call(Prompt)` uses `Prompt.getOptions()` as-is when non-null and the model defaults otherwise; there is no partial merge at that level.
- **Options are immutable.** `copy()` and `fromOptions(...)` are gone; derive a modified instance with `options.mutate()....build()`. Collections returned by option getters are unmodifiable.
- **Read defaults with `getOptions()`.** `ChatModel#getDefaultOptions()` and `*Properties#getOptions()` are deprecated. Test stubs and configuration tests should use `chatModel.getOptions()` and `OllamaChatProperties#getModel()` (or other flattened getters).
- **`ChatClient#mutate()` is for derived clients.** It returns a builder pre-loaded with an existing client's defaults so you can create a variant (different system prompt, advisors, or options). It is not required to retain `application.properties` values and should not appear in the primary `ChatClient` bean.
- **Tool loop lives in `ToolCallingAdvisor`.** It is auto-registered on the auto-configured `ChatClient` when tools are present; do not port 1.x `internalToolExecutionEnabled` handling.

## Make Model Output Explicit

Use dedicated response records or classes for structured output. Mark genuinely optional fields with `@Nullable` and test the generated JSON Schema's required set.

For provider-native structured output:

```java
ResponseEntity<ChatResponse, Output> exchange = chatClient.prompt()
	.system(systemPrompt)
	.user(userInput)
	.call()
	.responseEntity(Output.class,
			spec -> spec.useProviderStructuredOutput().validateSchema());
```

Treat successful JSON mapping as structural validation, not business validation. Apply deterministic application validation afterward. Classify timeouts, provider/schema failures, mapping failures, and semantic rejection separately when callers need different diagnostics or recovery.

## Verify the Integration

Keep ordinary tests independent of a running Ollama process.

- Bind `spring.ai.ollama.chat.model` and any model options in a configuration test through `OllamaChatProperties`, and assert that no `spring.ai.ollama.chat.options.*` key or custom model property is present in `application.properties`.
- Verify the request factory's connect and read timeouts using the test in the timeout reference.
- Exercise the `ChatClient` call with a stub `ChatModel` or mock HTTP server when prompt roles, native schema submission, typed mapping, or exception classification matter.
- Unit-test structured-output optionality and deterministic validation.
- Keep a live Ollama smoke test opt-in and document the exact required model tag.

When finishing, report the configured model property, timeout values, files changed, and commands actually run.
