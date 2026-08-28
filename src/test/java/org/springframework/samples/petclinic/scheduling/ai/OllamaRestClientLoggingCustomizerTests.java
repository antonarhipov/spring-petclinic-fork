package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaRestClientLoggingCustomizerTests {

	@Test
	void logsFullOllamaRequestAndResponseWithoutConsumingResponseBody() {
		Logger logger = (Logger) LoggerFactory.getLogger(OllamaRestClientLoggingCustomizer.class);
		Level previousLevel = logger.getLevel();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		logger.setLevel(Level.DEBUG);
		logger.addAppender(appender);
		appender.start();
		try {
			RestClient.Builder builder = RestClient.builder();
			new OllamaRestClientLoggingCustomizer("http://localhost:11434").customize(builder);
			MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
			server.expect(requestTo("http://localhost:11434/api/chat"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"prompt\":\"owner text\"}"))
				.andRespond(withSuccess("{\"response\":\"model text\"}", MediaType.APPLICATION_JSON));

			String response = builder.build()
				.post()
				.uri("http://localhost:11434/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"prompt\":\"owner text\"}")
				.retrieve()
				.body(String.class);

			server.verify();
			assertThat(response).isEqualTo("{\"response\":\"model text\"}");
			String logs = appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.collect(Collectors.joining("\n"));
			assertThat(logs).contains("body={\"prompt\":\"owner text\"}")
				.contains("status=200 OK body={\"response\":\"model text\"}");
		}
		finally {
			appender.stop();
			logger.detachAppender(appender);
			logger.setLevel(previousLevel);
		}
	}

}
