package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(assignableTypes = MyAppointmentsController.class)
public class OwnerAppointmentExceptionAdvice {

	@ExceptionHandler(IllegalAppointmentTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	String conflict(Model model) {
		model.addAttribute("status", HttpStatus.CONFLICT.value());
		return "error";
	}

}
