package org.springframework.samples.petclinic.scheduling.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyTerm;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyTermRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicPolicyService {

	private final AvailabilityRepository policies;

	private final AllowedDurationRepository durations;

	private final ClinicHoursRepository hours;

	private final NamedPeriodRepository periods;

	private final EmergencyTermRepository terms;

	private final ConfigurationVersionService versions;

	private final CapacityAuditService audit;

	private final SchedulingRequestRepository requests;

	private final AppointmentRepository appointments;

	public ClinicPolicyService(AvailabilityRepository policies, AllowedDurationRepository durations,
			ClinicHoursRepository hours, NamedPeriodRepository periods, EmergencyTermRepository terms,
			ConfigurationVersionService versions, CapacityAuditService audit, SchedulingRequestRepository requests,
			AppointmentRepository appointments) {
		this.policies = policies;
		this.durations = durations;
		this.hours = hours;
		this.periods = periods;
		this.terms = terms;
		this.versions = versions;
		this.audit = audit;
		this.requests = requests;
		this.appointments = appointments;
	}

	@Transactional
	public ClinicSchedulingPolicy updateBounds(int bookingHorizonDays, int holdDurationMinutes, Long actorAccountId) {
		if (bookingHorizonDays < 1 || bookingHorizonDays > 365) {
			throw new PolicyValidationException("HORIZON_BOUNDS");
		}
		if (holdDurationMinutes < 1 || holdDurationMinutes > 60) {
			throw new PolicyValidationException("HOLD_BOUNDS");
		}
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		String before = "{\"horizon\":" + policy.getBookingHorizonDays() + "}";
		policy.setBookingHorizonDays(bookingHorizonDays);
		policy.setHoldDurationMinutes(holdDurationMinutes);
		this.versions.bump();
		this.audit.record(actorAccountId, "POLICY_UPDATED", "POLICY", String.valueOf(policy.getId()), before,
				"{\"horizon\":" + bookingHorizonDays + "}");
		return policy;
	}

	@Transactional
	public void replaceDurations(List<Integer> minutes, Long actorAccountId) {
		for (int value : minutes) {
			if (value <= 0 || value % 15 != 0 || value > 240) {
				throw new PolicyValidationException("DURATION_GRID");
			}
		}
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		this.durations.deleteAll(this.durations.findByPolicyId(policy.getId()));
		for (int value : minutes) {
			AllowedDuration duration = new AllowedDuration();
			duration.setPolicyId(policy.getId());
			duration.setDurationMinutes(value);
			this.durations.save(duration);
		}
		this.versions.bump();
		this.audit.record(actorAccountId, "DURATIONS_UPDATED", "POLICY", String.valueOf(policy.getId()), null,
				minutes.toString());
	}

	@Transactional
	public void replaceHours(List<ClinicHours> replacement, Long actorAccountId) {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		this.hours.deleteAll(this.hours.findByPolicyId(policy.getId()));
		for (ClinicHours row : replacement) {
			assertGrid(row.getStartLocalTime(), row.getEndLocalTime());
			row.setPolicyId(policy.getId());
			this.hours.save(row);
		}
		this.versions.bump();
		this.audit.record(actorAccountId, "HOURS_UPDATED", "POLICY", String.valueOf(policy.getId()), null, null);
	}

	@Transactional
	public NamedPeriod saveNamedPeriod(NamedPeriod period, Long actorAccountId) {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		period.setPolicyId(policy.getId());
		assertGrid(period.getStartLocalTime(), period.getEndLocalTime());
		for (NamedPeriod existing : this.periods.findByPolicyId(policy.getId())) {
			if (!existing.getCode().equals(period.getCode()) && overlaps(existing.getStartLocalTime(),
					existing.getEndLocalTime(), period.getStartLocalTime(), period.getEndLocalTime())) {
				throw new PolicyValidationException("NAMED_PERIOD_OVERLAP");
			}
		}
		NamedPeriod saved = this.periods.save(period);
		this.versions.bump();
		this.audit.record(actorAccountId, "NAMED_PERIOD_UPDATED", "NAMED_PERIOD", saved.getCode(), null, null);
		return saved;
	}

	@Transactional
	public void updateUrgentGuidance(String guidance, Long actorAccountId) {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		policy.setUrgentCareGuidance(guidance);
		this.versions.bump();
		this.audit.record(actorAccountId, "GUIDANCE_UPDATED", "POLICY", String.valueOf(policy.getId()), null, null);
	}

	@Transactional
	public void addEmergencyTerm(String term, Long actorAccountId) {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		EmergencyTerm entity = new EmergencyTerm();
		entity.setPolicyId(policy.getId());
		entity.setTerm(term);
		entity.setRuleSetVersion("v" + (policy.getConfigurationVersion() + 1));
		this.terms.save(entity);
		this.versions.bump();
		this.audit.record(actorAccountId, "EMERGENCY_TERM_ADDED", "EMERGENCY_TERM", term, null, null);
	}

	public ClinicSchedulingPolicy current() {
		return this.policies.currentPolicy();
	}

	public List<NamedPeriod> namedPeriods() {
		return this.periods.findByPolicyId(current().getId())
			.stream()
			.sorted(Comparator.comparing(NamedPeriod::getStartLocalTime))
			.toList();
	}

	public List<ClinicHours> clinicHours() {
		return this.hours.findByPolicyId(current().getId());
	}

	public void assertZoneImmutable(String proposedZone) {
		if (!current().getZoneId().equals(proposedZone)
				&& (this.requests.count() > 0 || this.appointments.count() > 0)) {
			throw new PolicyValidationException("ZONE_IMMUTABLE");
		}
	}

	@Transactional
	public ClinicSchedulingPolicy changeZone(String proposedZone, Long actorAccountId) {
		assertZoneImmutable(proposedZone);
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		if (!policy.getZoneId().equals(proposedZone)) {
			String before = "{\"zoneId\":\"" + policy.getZoneId() + "\"}";
			policy.setZoneId(proposedZone);
			this.versions.bump();
			this.audit.record(actorAccountId, "ZONE_UPDATED", "POLICY", String.valueOf(policy.getId()), before,
					"{\"zoneId\":\"" + proposedZone + "\"}");
		}
		return policy;
	}

	public DayOfWeek[] days() {
		return DayOfWeek.values();
	}

	private void assertGrid(LocalTime start, LocalTime end) {
		if (start.getMinute() % 15 != 0 || end.getMinute() % 15 != 0 || !end.isAfter(start)) {
			throw new PolicyValidationException("GRID_ALIGNMENT");
		}
	}

	private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
		return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
	}

}
