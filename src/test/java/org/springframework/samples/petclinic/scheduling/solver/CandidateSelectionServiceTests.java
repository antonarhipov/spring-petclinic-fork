package org.springframework.samples.petclinic.scheduling.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityQueryService;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettings;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettingsRepository;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.WindowKind;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

class CandidateSelectionServiceTests {

	private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Amsterdam");

	private static final Instant MONDAY_MORNING = Instant.parse("2026-08-31T07:00:00Z");

	private static final Instant TUESDAY_AFTER_LUNCH = Instant.parse("2026-09-01T11:00:00Z");

	@Test
	void selectsThePreferredTuesdayIntervalInsteadOfAnEarlierMondaySlot() {
		CandidateSelectionService service = serviceWithAvailableSlots(MONDAY_MORNING, TUESDAY_AFTER_LUNCH);

		Optional<CandidateSlot> selected = service.select(tuesdayAfterLunchRevision());

		assertThat(selected).get().extracting(CandidateSlot::startAt).isEqualTo(TUESDAY_AFTER_LUNCH);
	}

	@Test
	void reportsNoPreferredSlotBeforeOfferingAnAlternative() {
		CandidateSelectionService service = serviceWithAvailableSlots(MONDAY_MORNING);
		RequestRevision revision = tuesdayAfterLunchRevision();

		assertThat(service.select(revision)).isEmpty();
		assertThat(service.selectAlternative(revision)).get()
			.extracting(CandidateSlot::startAt)
			.isEqualTo(MONDAY_MORNING);
	}

	private CandidateSelectionService serviceWithAvailableSlots(Instant... availableSlots) {
		Vet vet = new Vet();
		vet.setId(1);
		VetRepository vets = mock(VetRepository.class);
		when(vets.findAll()).thenReturn(List.of(vet));

		ClinicSchedulingSettings clinic = mock(ClinicSchedulingSettings.class);
		when(clinic.getClinicZone()).thenReturn(CLINIC_ZONE.getId());
		when(clinic.getOwnerMinimumNoticeMinutes()).thenReturn(120);
		when(clinic.getBookingHorizonDays()).thenReturn(7);
		ClinicSchedulingSettingsRepository settings = mock(ClinicSchedulingSettingsRepository.class);
		when(settings.findById(1)).thenReturn(Optional.of(clinic));

		AvailabilityQueryService availability = mock(AvailabilityQueryService.class);
		when(availability.isAvailable(eq(1), any(Instant.class), anyInt())).thenAnswer(invocation -> {
			Instant candidate = invocation.getArgument(1);
			return List.of(availableSlots).contains(candidate);
		});
		AppointmentOfferRepository offers = mock(AppointmentOfferRepository.class);
		when(offers.findByRevisionId(any())).thenReturn(List.of());
		ReservationBlockRepository reservations = mock(ReservationBlockRepository.class);

		return new CandidateSelectionService(vets, settings, availability, offers, reservations,
				Clock.fixed(Instant.parse("2026-08-28T20:30:00Z"), ZoneOffset.UTC));
	}

	private RequestRevision tuesdayAfterLunchRevision() {
		Pet pet = new Pet();
		pet.setId(1);
		SchedulingRequest request = new SchedulingRequest(pet, false);
		RequestRevision revision = new RequestRevision(request, 1, "Visit on Tuesday after lunch", true,
				Instant.parse("2026-08-28T20:30:00Z"), "test-correlation");
		revision.setId(1);
		revision.applyInterpretation("raw", "test", "Runny nose", 30, "GENERAL", null, null, "STANDARD");
		revision.replaceWindows(List.of(new RequestAvailabilityWindow(WindowKind.PREFERRED, LocalDate.of(2026, 9, 1),
				null, LocalTime.of(13, 0), LocalTime.of(17, 0), "owner request")));
		request.setCurrentRevision(revision);
		return revision;
	}

}
