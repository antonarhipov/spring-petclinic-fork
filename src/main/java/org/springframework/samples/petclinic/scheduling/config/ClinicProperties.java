package org.springframework.samples.petclinic.scheduling.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "petclinic.scheduling")
public class ClinicProperties {

	private String clinicZoneId = "UTC";

	public String getClinicZoneId() {
		return clinicZoneId;
	}

	public void setClinicZoneId(String clinicZoneId) {
		this.clinicZoneId = clinicZoneId;
	}

	@PostConstruct
	public void validate() {
		if (clinicZoneId == null || clinicZoneId.trim().isEmpty()) {
			clinicZoneId = "UTC";
		}
		try {
			ZoneId.of(clinicZoneId);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Invalid clinic time-zone property: " + clinicZoneId, ex);
		}
	}

}
