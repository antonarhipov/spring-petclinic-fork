package org.springframework.samples.petclinic.scheduling.service;

import java.time.LocalTime;
import java.util.Collection;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.samples.petclinic.scheduling.model.ShiftType;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoScheduleInitializer {

	private final VetScheduleRepository vetScheduleRepository;

	private final VetRepository vetRepository;

	private final VetScheduleService vetScheduleService;

	public DemoScheduleInitializer(VetScheduleRepository vetScheduleRepository, VetRepository vetRepository,
			VetScheduleService vetScheduleService) {
		this.vetScheduleRepository = vetScheduleRepository;
		this.vetRepository = vetRepository;
		this.vetScheduleService = vetScheduleService;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void initializeDefaultSchedules() {
		if (vetScheduleRepository.count() > 0) {
			return;
		}

		Collection<Vet> vets = vetRepository.findAll();
		LocalTime defaultStart = LocalTime.of(9, 0);
		LocalTime defaultEnd = LocalTime.of(17, 0);

		for (Vet vet : vets) {
			for (int dayOfWeek = 1; dayOfWeek <= 5; dayOfWeek++) {
				vetScheduleService.createWeeklySchedule(vet.getId(), dayOfWeek, defaultStart, defaultEnd,
						ShiftType.REGULAR);
			}
		}
	}

}
