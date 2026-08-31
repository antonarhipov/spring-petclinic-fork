package org.springframework.samples.petclinic.shared.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

/**
 * Adds a fresh command identifier to each rendered mutation form while retaining the
 * hidden fields contributed by Spring Security's CSRF processor.
 */
final class CommandFormValueProcessor implements RequestDataValueProcessor {

	private static final String COMMAND_ID_ATTRIBUTE = CommandFormValueProcessor.class.getName() + ".commandId";

	private final RequestDataValueProcessor delegate;

	CommandFormValueProcessor(RequestDataValueProcessor delegate) {
		this.delegate = delegate;
	}

	@Override
	public String processAction(HttpServletRequest request, String action, String httpMethod) {
		String processedAction = this.delegate.processAction(request, action, httpMethod);
		if (isProtectedMutation(request, processedAction, httpMethod)) {
			request.setAttribute(COMMAND_ID_ATTRIBUTE, UUID.randomUUID().toString());
		}
		else {
			request.removeAttribute(COMMAND_ID_ATTRIBUTE);
		}
		return processedAction;
	}

	@Override
	public String processFormFieldValue(HttpServletRequest request, @Nullable String name, String value, String type) {
		return this.delegate.processFormFieldValue(request, name, value, type);
	}

	@Override
	public Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
		Map<String, String> fields = new LinkedHashMap<>(this.delegate.getExtraHiddenFields(request));
		Object commandId = request.getAttribute(COMMAND_ID_ATTRIBUTE);
		request.removeAttribute(COMMAND_ID_ATTRIBUTE);
		if (commandId != null) {
			fields.put("commandId", commandId.toString());
		}
		return fields;
	}

	@Override
	public String processUrl(HttpServletRequest request, String url) {
		return this.delegate.processUrl(request, url);
	}

	private static boolean isProtectedMutation(HttpServletRequest request, String action, String httpMethod) {
		if (httpMethod == null || !"POST".equalsIgnoreCase(httpMethod)) {
			return false;
		}
		String path = action;
		if (path == null || path.isBlank()) {
			path = request.getRequestURI();
		}
		return path.startsWith("/owner/") || path.startsWith("/staff/") || path.startsWith("/account/")
				|| path.startsWith("/auth/password-change");
	}

}
