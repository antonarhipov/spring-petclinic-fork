package org.springframework.samples.petclinic.scheduling.interpretation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnresolvedItem(String code, String detail) {
}
