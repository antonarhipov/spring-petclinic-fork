package org.springframework.samples.petclinic.system;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingExceptionHandlerTests {

	private final SchedulingExceptionHandler handler = new SchedulingExceptionHandler();

	@Test
	void htmlConflictRendersStalePageWithSubmittedValues() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Accept", "text/html");
		Object result = this.handler.handleStale(new StaleStateException("stale", "current", "kept"), request);
		assertThat(result).isInstanceOf(ModelAndView.class);
		ModelAndView mav = (ModelAndView) result;
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(mav.getViewName()).isEqualTo("error/stale");
		assertThat(mav.getModel().get("submittedValues")).isEqualTo("kept");
	}

	@Test
	void jsonConflictReturnsProblemDetail() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Accept", "application/json");
		Object result = this.handler.handleStale(new StaleStateException("stale", "current", "kept"), request);
		assertThat(result).isInstanceOf(ProblemDetail.class);
		ProblemDetail detail = (ProblemDetail) result;
		assertThat(detail.getStatus()).isEqualTo(409);
		assertThat(detail.getTitle()).isEqualTo("Stale state");
		assertThat(detail.getProperties()).containsKey("submittedValues");
	}

}
