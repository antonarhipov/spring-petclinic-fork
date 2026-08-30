package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.NamedPeriod;
import org.springframework.samples.petclinic.scheduling.availability.NamedPeriodRepository;
import org.springframework.stereotype.Service;

@Service
public class NamedPeriodSnapshotService {

	private final NamedPeriodRepository periods;

	private final AvailabilityRepository policies;

	public NamedPeriodSnapshotService(NamedPeriodRepository periods, AvailabilityRepository policies) {
		this.periods = periods;
		this.policies = policies;
	}

	public void snapshot(RequestWindow window, LocalDate date) {
		if (window.getNamedPeriodCode() == null) {
			return;
		}
		NamedPeriod period = this.periods.findByCode(window.getNamedPeriodCode()).orElseThrow();
		ZoneId zone = ZoneId.of(this.policies.currentPolicy().getZoneId());
		window.setStartAt(date.atTime(period.getStartLocalTime()).atZone(zone).toInstant());
		window.setEndAt(date.atTime(period.getEndLocalTime()).atZone(zone).toInstant());
	}

}
