package org.springframework.samples.petclinic.scheduling;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.system.SecurityConfig;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class RuntimePersistenceRestartTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void uc3G18_fileBackedStateSurvivesRestartAndStartupRecoversInterpretingRequests() {
		String databaseUrl = "jdbc:h2:file:" + this.temporaryDirectory.resolve("restart-petclinic").toAbsolutePath()
				+ ";DB_CLOSE_ON_EXIT=FALSE";
		int acceptedRequest;
		int recoveringRequest;
		int appointment;

		try (ConfigurableApplicationContext first = start(databaseUrl)) {
			JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
			RequestService requests = first.getBean(RequestService.class);
			acceptedRequest = interpretedRequest(requests, jdbc, 1);
			requests.confirmInterpretation(acceptedRequest);
			requests.anotherSuggestion(acceptedRequest);
			requests.acceptSuggestion(acceptedRequest);
			appointment = jdbc.queryForObject("select id from appointments where request_id = ?", Integer.class,
					acceptedRequest);

			recoveringRequest = requests.startForOwner(2, "Persisted interpreting request").request().getId();
			jdbc.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", recoveringRequest);
			jdbc.update("update clinic_settings set booking_horizon_days = 45");
		}

		try (ConfigurableApplicationContext second = start(databaseUrl)) {
			JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
			assertThat(jdbc.queryForMap(
					"select state, active_pet_id, request_text from scheduling_requests where id = ?", acceptedRequest))
				.containsEntry("STATE", "ACCEPTED")
				.containsEntry("REQUEST_TEXT", "Persisted scheduling request")
				.containsEntry("ACTIVE_PET_ID", null);
			assertThat(jdbc.queryForMap(
					"select origin, cast(raw_json as varchar) raw_json, model_tag, prompt_version, duration_minutes from interpretations where request_id = ?",
					acceptedRequest))
				.containsEntry("ORIGIN", "AI")
				.containsEntry("RAW_JSON", "{\"persisted\":true}")
				.containsEntry("MODEL_TAG", "ministral-3:14b")
				.containsEntry("PROMPT_VERSION", "v1")
				.containsEntry("DURATION_MINUTES", 30);
			assertThat(jdbc.queryForList(
					"select window_kind, weekday, start_time, end_time from interpretation_windows where interpretation_id = (select current_interpretation_id from scheduling_requests where id = ?)",
					acceptedRequest))
				.singleElement()
				.satisfies(row -> assertThat(row).containsEntry("WINDOW_KIND", "PREFERRED")
					.containsEntry("WEEKDAY", "TUESDAY"));
			assertThat(jdbc.queryForObject("select count(*) from request_rejections where request_id = ?",
					Integer.class, acceptedRequest))
				.isOne();
			assertThat(jdbc.queryForMap("select request_id, status, appointment_date from appointments where id = ?",
					appointment))
				.containsEntry("REQUEST_ID", acceptedRequest)
				.containsEntry("STATUS", "CONFIRMED");
			assertThat(jdbc.queryForObject("select booking_horizon_days from clinic_settings", Integer.class))
				.isEqualTo(45);
			assertThat(jdbc.queryForMap("select state, with_staff_reason from scheduling_requests where id = ?",
					recoveringRequest))
				.containsAllEntriesOf(Map.of("STATE", "WITH_STAFF", "WITH_STAFF_REASON", "AI_UNAVAILABLE"));
		}
	}

	private int interpretedRequest(RequestService requests, JdbcTemplate jdbc, int petId) {
		int requestId = requests.startForOwner(petId, "Persisted scheduling request").request().getId();
		jdbc.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", requestId);
		Interpretation interpretation = new Interpretation(true, CareType.SPECIALTY, "dentistry", null, 30, null,
				InterpretationOrigin.AI, "{\"persisted\":true}", "ministral-3:14b", "v1", LocalDate.of(2026, 9, 7),
				LocalTime.of(9, 0));
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		requests.interpretationSucceeded(requestId, interpretation);
		return requestId;
	}

	private ConfigurableApplicationContext start(String databaseUrl) {
		return new SpringApplicationBuilder(PersistenceTestApplication.class).profiles("test")
			.web(WebApplicationType.NONE)
			.run("--spring.datasource.url=" + databaseUrl, "--spring.datasource.username=sa",
					"--spring.datasource.password=", "--spring.flyway.enabled=true",
					"--spring.jpa.hibernate.ddl-auto=none", "--scheduling.interpretation.deadline=250ms");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@AutoConfigurationPackage(basePackages = "org.springframework.samples.petclinic")
	@EntityScan("org.springframework.samples.petclinic")
	@EnableJpaRepositories("org.springframework.samples.petclinic")
	@ComponentScan(basePackages = "org.springframework.samples.petclinic",
			excludeFilters = {
					@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PetClinicApplication.class),
					@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class) })
	static class PersistenceTestApplication {

	}

}
