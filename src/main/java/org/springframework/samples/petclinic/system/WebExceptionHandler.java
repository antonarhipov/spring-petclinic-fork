package org.springframework.samples.petclinic.system;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
		log.warn("Access denied for request {}: {}", request.getRequestURI(), ex.getMessage());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "access_denied", "message", ex.getMessage()));
		}
		response.setStatus(HttpStatus.FORBIDDEN.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.FORBIDDEN.value());
		mav.addObject("message", "Access Denied: You do not have permission to access this resource.");
		return mav;
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public Object handleOptimisticLockFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Optimistic lock conflict for request {}: {}", request.getRequestURI(), ex.getMessage());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "conflict", "message",
						"The record was modified by another transaction. Please retry."));
		}
		response.setStatus(HttpStatus.CONFLICT.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.CONFLICT.value());
		mav.addObject("message", "A concurrent modification occurred. Please refresh and try again.");
		return mav;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.warn("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "bad_request", "message", ex.getMessage()));
		}
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.BAD_REQUEST.value());
		mav.addObject("message", ex.getMessage());
		return mav;
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.debug("Path/parameter type mismatch on {}: {}", request.getRequestURI(), ex.getMessage());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "not_found", "message", "Resource not found"));
		}
		response.setStatus(HttpStatus.NOT_FOUND.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.NOT_FOUND.value());
		mav.addObject("message", "The requested resource was not found.");
		return mav;
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request,
			HttpServletResponse response) {
		log.debug("No resource for {}: {}", request.getRequestURI(), ex.getMessage());
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "not_found", "message", "Resource not found"));
		}
		response.setStatus(HttpStatus.NOT_FOUND.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.NOT_FOUND.value());
		mav.addObject("message", "The requested page was not found.");
		return mav;
	}

	@ExceptionHandler(Exception.class)
	public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
		log.error("Unhandled error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		if (isJsonRequest(request)) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "server_error", "message", "An unexpected error occurred."));
		}
		response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		ModelAndView mav = new ModelAndView("error");
		mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
		mav.addObject("message", "An unexpected error occurred. Please try again later.");
		return mav;
	}

	private boolean isJsonRequest(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		String requestedWith = request.getHeader("X-Requested-With");
		return (accept != null && accept.contains("application/json")) || "XMLHttpRequest".equals(requestedWith);
	}

}
