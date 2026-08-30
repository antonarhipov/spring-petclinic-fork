package org.springframework.samples.petclinic.system;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class SchedulingExceptionHandler {

	@ExceptionHandler({ StaleStateException.class, ObjectOptimisticLockingFailureException.class })
	@ResponseStatus(HttpStatus.CONFLICT)
	public Object handleStale(Exception ex, HttpServletRequest request) {
		if (wantsJson(request)) {
			ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The page is out of date.");
			detail.setTitle("Stale state");
			if (ex instanceof StaleStateException stale) {
				detail.setProperty("currentState", stale.getCurrentState());
				detail.setProperty("submittedValues", stale.getSubmittedValues());
			}
			return detail;
		}
		ModelAndView mav = new ModelAndView("error/stale");
		mav.setStatus(HttpStatus.CONFLICT);
		if (ex instanceof StaleStateException stale) {
			mav.addObject("currentState", stale.getCurrentState());
			mav.addObject("submittedValues", stale.getSubmittedValues());
		}
		return mav;
	}

	private static boolean wantsJson(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
	}

}
