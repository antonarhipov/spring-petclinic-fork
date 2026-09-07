package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class OwnerResourceNotFoundAdvice {

	@ExceptionHandler(OwnerResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound(Model model) {
		model.addAttribute("status", HttpStatus.NOT_FOUND.value());
		return "error";
	}

}
