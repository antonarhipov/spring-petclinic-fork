package org.springframework.samples.petclinic.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NavigationSecurityTests {

	@Test
	void ownerTemplateDoesNotContainCalendarRoute() throws Exception {
		String ownerTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/offer.html"));
		assertThat(ownerTemplate).doesNotContain("/staff/calendar");
	}

	@Test
	void reviewTemplateSelectsTheInterpretedDuration() throws Exception {
		String reviewTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/reviewRequest.html"));
		assertThat(reviewTemplate).contains("th:selected=\"${d == request.currentRevision.durationMinutes}\"");
	}

	@Test
	void sharedLayoutProvidesAccessibleSubmissionFeedback() throws Exception {
		String layout = Files.readString(Path.of("src/main/resources/templates/fragments/layout.html"));
		String script = Files.readString(Path.of("src/main/resources/static/resources/js/petclinic.js"));

		assertThat(layout).contains("role=\"status\"", "aria-live=\"polite\"", "/resources/js/petclinic.js");
		assertThat(script).contains("aria-busy", "control.disabled = true", "pageshow");
	}

	@Test
	void slowSchedulingFormsUseActionSpecificFeedback() throws Exception {
		String requestTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/newRequest.html"));
		String reviewTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/reviewRequest.html"));

		assertThat(requestTemplate).contains("data-loading-message=#{loading.interpreting}");
		assertThat(reviewTemplate).contains("data-loading-message=#{loading.findingAppointment}");
	}

	@Test
	void schedulingTemplatesUseClinicLocalDateAndTime() throws Exception {
		String offerTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/offer.html"));
		String dashboardTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/dashboard.html"));
		String calendarTemplate = Files.readString(Path.of("src/main/resources/templates/staff/calendar.html"));
		String appointmentTemplate = Files.readString(Path.of("src/main/resources/templates/staff/appointment.html"));

		assertThat(offerTemplate).contains("@clinicDateTime.formatDate(offer.startAt)",
				"@clinicDateTime.formatTime(offer.startAt)", "@clinicDateTime.format(offer.expiresAt)");
		assertThat(dashboardTemplate).contains("@clinicDateTime.format(appointment.startAt)");
		assertThat(calendarTemplate).contains("@clinicDateTime.format(appointment.startAt)",
				"@clinicDateTime.format(offer.startAt)");
		assertThat(appointmentTemplate).contains("@clinicDateTime.format(appointment.startAt)");
	}

}
