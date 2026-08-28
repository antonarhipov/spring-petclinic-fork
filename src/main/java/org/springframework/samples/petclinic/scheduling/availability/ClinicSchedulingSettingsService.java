package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;

@Service
public class ClinicSchedulingSettingsService {

	private final ClinicSchedulingSettingsRepository settings;

	private final NamedDayPeriodRepository periods;

	private final SchedulingRequestRepository requests;

	private final AppointmentRepository appointments;

	private final SchedulingAuditService audit;

	public ClinicSchedulingSettingsService(ClinicSchedulingSettingsRepository settings,
			NamedDayPeriodRepository periods, SchedulingRequestRepository requests, AppointmentRepository appointments,
			SchedulingAuditService audit) {
		this.settings = settings;
		this.periods = periods;
		this.requests = requests;
		this.appointments = appointments;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public ClinicSchedulingSettings get() {
		return this.settings.findById(1).orElseThrow();
	}

	@Transactional
	public ClinicSchedulingSettings update(String zone, int horizon, int notice, int hold, String guidance,
			Authentication actor) {
		validate(zone, horizon, notice, hold, guidance);
		ClinicSchedulingSettings setting = get();
		if (!setting.getClinicZone().equals(zone) && (this.requests.count() > 0 || this.appointments.count() > 0)) {
			throw new IllegalStateException("The clinic time zone cannot change after scheduling starts");
		}
		setting.update(zone, horizon, notice, hold, guidance);
		this.audit.record(actor, null, AuditAction.SETTINGS_UPDATED, "settings", 1, null, "updated", null);
		return setting;
	}

	@Transactional
	public NamedDayPeriod addPeriod(String name, LocalTime start, LocalTime end, Authentication actor) {
		validateQuarter(start);
		validateQuarter(end);
		NamedDayPeriod period = new NamedDayPeriod(name, start, end);
		List<NamedDayPeriod> all = this.periods.findAll();
		if (all.stream()
			.anyMatch(existing -> start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime()))) {
			throw new IllegalArgumentException("Named day periods cannot overlap");
		}
		NamedDayPeriod saved = this.periods.save(period);
		this.audit.record(actor, null, AuditAction.SETTINGS_UPDATED, "named-period", saved.getId(), null, name, null);
		return saved;
	}

	private void validate(String zone, int horizon, int notice, int hold, String guidance) {
		ZoneId.of(zone);
		if (horizon < 1 || horizon > 365 || hold < 1 || hold > 60 || notice < 0 || guidance == null
				|| guidance.isBlank()) {
			throw new IllegalArgumentException("Scheduling settings are outside their allowed bounds");
		}
	}

	private void validateQuarter(LocalTime time) {
		if (time == null || time.getMinute() % 15 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
			throw new IllegalArgumentException("Scheduling times must align to the 15-minute grid");
		}
	}

}
