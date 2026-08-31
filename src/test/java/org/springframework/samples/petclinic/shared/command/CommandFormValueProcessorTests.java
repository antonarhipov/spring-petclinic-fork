package org.springframework.samples.petclinic.shared.command;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class CommandFormValueProcessorTests {

	private final CommandFormValueProcessor processor = new CommandFormValueProcessor(
			new CsrfRequestDataValueProcessor());

	@Test
	void addsUniqueCommandIdsAndPreservesCsrfForMutationForms() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-token");
		request.setAttribute(CsrfToken.class.getName(), csrfToken);

		this.processor.processAction(request, "/owner/requests", "post");
		Map<String, String> first = this.processor.getExtraHiddenFields(request);
		this.processor.processAction(request, "/owner/requests", "post");
		Map<String, String> second = this.processor.getExtraHiddenFields(request);

		assertThat(first).containsEntry("_csrf", "csrf-token").containsKey("commandId");
		assertThat(second).containsEntry("_csrf", "csrf-token").containsKey("commandId");
		assertThat(first.get("commandId")).isNotEqualTo(second.get("commandId"));
	}

	@Test
	void doesNotAddCommandIdToReadOnlyForms() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		this.processor.processAction(request, "/staff/queue", "get");

		assertThat(this.processor.getExtraHiddenFields(request)).doesNotContainKey("commandId");
	}

}
