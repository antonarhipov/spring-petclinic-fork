package org.springframework.samples.petclinic.system;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.RecurringShift;
import org.springframework.samples.petclinic.availability.RecurringShiftRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchedulingDemoDataSeeder {

	private static final Logger log = LoggerFactory.getLogger(SchedulingDemoDataSeeder.class);

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final RecurringShiftRepository recurringShiftRepository;

	public SchedulingDemoDataSeeder(ClinicPolicyRepository clinicPolicyRepository,
			RecurringShiftRepository recurringShiftRepository) {
		this.clinicPolicyRepository = Objects.requireNonNull(clinicPolicyRepository,
				"clinicPolicyRepository must not be null");
		this.recurringShiftRepository = Objects.requireNonNull(recurringShiftRepository,
				"recurringShiftRepository must not be null");
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seedDemoData() {
		seedClinicPolicy();
		seedDefaultShifts();
	}

	private void seedClinicPolicy() {
		if (this.clinicPolicyRepository.findById(1).isEmpty()) {
			ClinicPolicy policy = ClinicPolicy.createDefaultPolicy();
			policy.setId(1);
			policy.setZoneId("Europe/Amsterdam");
			policy.setBookingHorizonDays(90);
			policy.setHoldDurationMinutes(10);
			policy.setOwnerNoticeMinutes(120);
			policy.setStartGridMinutes(15);
			this.clinicPolicyRepository.save(policy);
			log.info("Seeded default clinic policy with zone {}", policy.getZoneId());
		}
	}

	private void seedDefaultShifts() {
		if (this.recurringShiftRepository.count() == 0) {
			for (int vetId = 1; vetId <= 6; vetId++) {
				for (DayOfWeek day : new DayOfWeek[] { DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
						DayOfWeek.THURSDAY, DayOfWeek.FRIDAY }) {
					RecurringShift shift = new RecurringShift(vetId, day, LocalTime.of(9, 0), LocalTime.of(17, 0));
					this.recurringShiftRepository.save(shift);
				}
			}
			log.info("Seeded default Monday-Friday 09:00-17:00 recurring shifts for veterinarians 1..6");
		}
	}

}
