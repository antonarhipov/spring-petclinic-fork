package org.springframework.samples.petclinic.system;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class WebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Access denied for request {}; category={}", request.getRequestURI(), ex.getClass().getSimpleName());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(errorBody("access_denied", "You do not have permission to access this resource.", request));
		}
		response.setStatus(HttpStatus.FORBIDDEN.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.FORBIDDEN.value());
		mav.addObject("message", "Access Denied: You do not have permission to access this resource.");
		return withNextAction(mav, request);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public Object handleOptimisticLockFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Optimistic lock conflict for request {}; category={}", request.getRequestURI(),
				ex.getClass().getSimpleName());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody("conflict", "This record changed while you were editing it. Refresh and try again.",
						request));
		}
		response.setStatus(HttpStatus.CONFLICT.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.CONFLICT.value());
		mav.addObject("message", "A concurrent modification occurred. Please refresh and try again.");
		return withNextAction(mav, request);
	}

	@ExceptionHandler(AvailabilityConflictException.class)
	public Object handleAvailabilityConflict(AvailabilityConflictException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Availability conflict for request {}; category={}", request.getRequestURI(),
				ex.getClass().getSimpleName());
		String message = "That time is no longer available. Review the current calendar and choose another time.";
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody("availability_conflict", message, request));
		}
		response.setStatus(HttpStatus.CONFLICT.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.CONFLICT.value());
		mav.addObject("message", message);
		return withNextAction(mav, request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Invalid request on {}; category={}", request.getRequestURI(), ex.getClass().getSimpleName());
		String message = "Review the submitted values and try again.";
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("validation", message, request));
		}
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.BAD_REQUEST.value());
		mav.addObject("message", message);
		return withNextAction(mav, request);
	}

	@ExceptionHandler(IllegalStateException.class)
	public Object handleIllegalState(IllegalStateException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("State conflict on {}; category={}", request.getRequestURI(), ex.getClass().getSimpleName());
		String message = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired")
				? "This offer or action has expired. Return to the current request and choose the available next action."
				: "This action is no longer valid for the current state. Refresh the page and try the available next action.";
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("state_conflict", message, request));
		}
		response.setStatus(HttpStatus.CONFLICT.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.CONFLICT.value());
		mav.addObject("message", message);
		return withNextAction(mav, request);
	}

	@ExceptionHandler(SecurityException.class)
	public Object handleProtectedDataFailure(SecurityException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.error("Protected data unavailable on {}; category={}", request.getRequestURI(),
				ex.getClass().getSimpleName());
		String message = "Protected information is temporarily unavailable. No data was exposed or changed. Please contact clinic staff.";
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(errorBody("protected_data_unavailable", message, request));
		}
		response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.SERVICE_UNAVAILABLE.value());
		mav.addObject("message", message);
		return withNextAction(mav, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.debug("Path or parameter type mismatch on {}; category={}", request.getRequestURI(),
				ex.getClass().getSimpleName());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(errorBody("not_found", "Resource not found", request));
		}
		response.setStatus(HttpStatus.NOT_FOUND.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.NOT_FOUND.value());
		mav.addObject("message", "The requested resource was not found.");
		return withNextAction(mav, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.debug("No resource for {}; category={}", request.getRequestURI(), ex.getClass().getSimpleName());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(errorBody("not_found", "Resource not found", request));
		}
		response.setStatus(HttpStatus.NOT_FOUND.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.NOT_FOUND.value());
		mav.addObject("message", "The requested page was not found.");
		return withNextAction(mav, request);
	}

	@ExceptionHandler(Exception.class)
	public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
		log.error("Unhandled error on {}; category={}", request.getRequestURI(), ex.getClass().getSimpleName());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(errorBody("server_error", "An unexpected error occurred. Your prior data was not discarded.",
						request));
		}
		response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
		mav.addObject("message", "An unexpected error occurred. Please try again later.");
		return withNextAction(mav, request);
	}

	private Map<String, String> errorBody(String error, String message, HttpServletRequest request) {
		return Map.of("error", error, "message", message, "nextAction", nextAction(request));
	}

	private ModelAndView withNextAction(ModelAndView mav, HttpServletRequest request) {
		mav.addObject("nextAction", nextAction(request));
		return mav;
	}

	private String nextAction(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri.startsWith("/owner/")) {
			return "/owner/dashboard";
		}
		if (uri.startsWith("/staff/")) {
			return "/staff/queue";
		}
		return "/";
	}

	private boolean isJsonRequest(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		String requestedWith = request.getHeader("X-Requested-With");
		return (accept != null && accept.contains("application/json")) || "XMLHttpRequest".equals(requestedWith);
	}

}
