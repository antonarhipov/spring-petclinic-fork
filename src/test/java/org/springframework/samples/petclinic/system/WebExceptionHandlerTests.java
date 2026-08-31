package org.springframework.samples.petclinic.system;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class WebExceptionHandlerTests {

	private final WebExceptionHandler handler = new WebExceptionHandler();

	@Test
	void validationResponseDoesNotLeakSubmittedOrInternalDetails() {
		MockHttpServletRequest request = request("/owner/requests/1", false);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ModelAndView result = (ModelAndView) this.handler.handleIllegalArgument(
				new IllegalArgumentException("raw owner prose and secret-key-123"), request, response);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(result.getModel().get("message").toString()).doesNotContain("raw owner prose", "secret-key-123");
		assertThat(result.getModel()).containsEntry("nextAction", "/owner/dashboard");
	}

	@Test
	void concurrencyAndAvailabilityFailuresReturnSafeJsonWithRecoveryAction() {
		MockHttpServletRequest staffRequest = request("/staff/calendar", true);
		ResponseEntity<?> concurrency = (ResponseEntity<?>) this.handler.handleOptimisticLockFailure(
				new ObjectOptimisticLockingFailureException("Appointment", 1L), staffRequest,
				new MockHttpServletResponse());
		ResponseEntity<?> availability = (ResponseEntity<?>) this.handler.handleAvailabilityConflict(
				new AvailabilityConflictException("owner 7 blocked by hold 99"), staffRequest,
				new MockHttpServletResponse());

		assertThat(concurrency.getStatusCode().value()).isEqualTo(409);
		assertThat(availability.getStatusCode().value()).isEqualTo(409);
		assertSafeStaffBody(concurrency.getBody());
		assertSafeStaffBody(availability.getBody());
	}

	@Test
	void genericFailureDoesNotExposeExceptionMessage() {
		MockHttpServletRequest request = request("/staff/queue", true);
		ResponseEntity<?> result = (ResponseEntity<?>) this.handler.handleGenericException(
				new RuntimeException("jdbc:h2:file:/secret/path password=hunter2"), request,
				new MockHttpServletResponse());

		assertThat(result.getStatusCode().value()).isEqualTo(500);
		assertThat(result.getBody().toString()).doesNotContain("jdbc:h2", "hunter2", "/secret/path");
		assertSafeStaffBody(result.getBody());
	}

	private MockHttpServletRequest request(String uri, boolean json) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		if (json) {
			request.addHeader("Accept", "application/json");
		}
		return request;
	}

	@SuppressWarnings("unchecked")
	private void assertSafeStaffBody(Object body) {
		assertThat(body).isInstanceOf(Map.class);
		Map<String, String> values = (Map<String, String>) body;
		assertThat(values).containsEntry("nextAction", "/staff/queue");
		assertThat(values.get("message")).doesNotContain("owner 7", "hold 99", "jdbc:h2", "hunter2");
	}

}
