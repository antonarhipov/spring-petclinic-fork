package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class OwnerResourceNotFoundException extends RuntimeException {

}
