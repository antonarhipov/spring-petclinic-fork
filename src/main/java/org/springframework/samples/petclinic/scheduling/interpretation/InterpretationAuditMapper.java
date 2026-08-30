package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class InterpretationAuditMapper {

	private final ObjectMapper mapper = new ObjectMapper();

	public String unknownFieldsJson(Map<String, Object> unknownFields) {
		try {
			return this.mapper.writeValueAsString(unknownFields);
		}
		catch (Exception ex) {
			return "{}";
		}
	}

}
