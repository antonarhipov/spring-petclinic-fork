/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.clinic.ClinicClosure;
import org.springframework.samples.petclinic.clinic.ClinicClosureRepository;
import org.springframework.samples.petclinic.clinic.ClinicSettings;
import org.springframework.samples.petclinic.clinic.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.OccupancyType;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.vet.VetWeeklyShift;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ HoldService.class, BookingService.class, AvailabilityService.class })
class BookingServiceTests {

	@Autowired
	private BookingService bookingService;

	@Autowired
	private HoldService holdService;

	@Autowired
	private AvailabilityService availabilityService;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private HoldRepository holds;

	@Autowired
	private SlotOccupancyRepository slotOccupancies;

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	@Autowired
	private VetWeeklyShiftRepository weeklyShifts;

	@Autowired
	private ClinicSettingsRepository clinicSettings;

	@Autowired
	private EntityManager entityManager;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private Vet vet2;

	@BeforeEach
	void setUp() {
		this.owner = this.owners.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
		this.vet = this.vets.findById(1).orElseThrow();
		this.vet2 = this.vets.findById(2).orElseThrow();

		// Configure weekly shifts for vet 1 and vet 2 across all days of the week from
		// 08:00 to 18:00
		for (DayOfWeek day : DayOfWeek.values()) {
			this.weeklyShifts.saveAndFlush(new VetWeeklyShift(this.vet, day, LocalTime.of(8, 0), LocalTime.of(18, 0)));
			this.weeklyShifts.saveAndFlush(new VetWeeklyShift(this.vet2, day, LocalTime.of(8, 0), LocalTime.of(18, 0)));
		}
	}

	@Test
	void shouldAcceptHoldBeforeExpiryAndCreateAppointment() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setRawText("Checkup and vaccines");
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(10, 30));

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));

		Appointment appointment = this.bookingService.acceptHold(request.getId());

		assertThat(appointment.getId()).isNotNull();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appointment.getOwner().getId()).isEqualTo(this.owner.getId());
		assertThat(appointment.getPet().getId()).isEqualTo(this.pet.getId());
		assertThat(appointment.getVet().getId()).isEqualTo(this.vet.getId());
		assertThat(appointment.getSchedulingRequest().getId()).isEqualTo(request.getId());
		assertThat(appointment.getStartTime()).isEqualTo(start);
		assertThat(appointment.getEndTime()).isEqualTo(end);

		Hold reloadedHold = this.holds.findById(hold.getId()).orElseThrow();
		assertThat(reloadedHold.getStatus()).isEqualTo(HoldStatus.CONSUMED);

		SlotOccupancy occupancy = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start).orElseThrow();
		assertThat(occupancy.getOccupancyType()).isEqualTo(OccupancyType.APPOINTMENT);
		assertThat(occupancy.getReferenceId()).isEqualTo(appointment.getId());

		SchedulingRequest reloadedRequest = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(reloadedRequest.getState()).isEqualTo(RequestState.CONFIRMED);
		assertThat(reloadedRequest.getActivePetKey()).isNull();
	}

	@Test
	void shouldRefuseAcceptWhenHoldHasExpired() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(11, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(11, 30));

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));
		hold.setExpiresAt(Instant.now().minusSeconds(60));
		this.holds.saveAndFlush(hold);

		final Integer reqId = request.getId();
		assertThatThrownBy(() -> this.bookingService.acceptHold(reqId)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("expired");

		Hold reloadedHold = this.holds.findById(hold.getId()).orElseThrow();
		assertThat(reloadedHold.getStatus()).isEqualTo(HoldStatus.EXPIRED);

		Optional<SlotOccupancy> occupancy = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occupancy).isEmpty();

		SchedulingRequest reloadedRequest = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(reloadedRequest.getState()).isEqualTo(RequestState.SUGGESTING);
	}

	@Test
	void shouldBookDirectWithoutSchedulingRequestAndCreateOccupancy() {
		// AC-51: Staff direct booking
		LocalDate targetDate = LocalDate.now().plusDays(3);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(9, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(9, 30));

		Appointment appointment = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				start, end, "Direct staff booking");

		assertThat(appointment.getId()).isNotNull();
		assertThat(appointment.getSchedulingRequest()).isNull();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appointment.getReason()).isEqualTo("Direct staff booking");

		Optional<SlotOccupancy> occOpt = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occOpt).isPresent();
		assertThat(occOpt.get().getOccupancyType()).isEqualTo(OccupancyType.APPOINTMENT);
		assertThat(occOpt.get().getReferenceId()).isEqualTo(appointment.getId());
	}

	@Test
	void shouldRejectDirectBookingWhenSlotConflicts() {
		LocalDate targetDate = LocalDate.now().plusDays(3);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(9, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(9, 30));

		this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start, end, "First");

		assertThatThrownBy(() -> this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				start, end, "Conflict"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("conflicts with an existing appointment");
	}

	@Test
	void shouldRescheduleAppointmentAndRecordChangeReason() {
		// AC-52: Staff reschedule re-validates and records reason
		LocalDate targetDate = LocalDate.now().plusDays(4);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(10, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Initial");

		LocalDateTime newStart = LocalDateTime.of(targetDate, LocalTime.of(14, 0));
		LocalDateTime newEnd = LocalDateTime.of(targetDate, LocalTime.of(14, 30));

		Appointment rescheduled = this.bookingService.rescheduleAppointment(appt.getId(), this.vet2.getId(), newStart,
				newEnd, "Owner requested afternoon with vet 2");

		assertThat(rescheduled.getVet().getId()).isEqualTo(this.vet2.getId());
		assertThat(rescheduled.getStartTime()).isEqualTo(newStart);
		assertThat(rescheduled.getEndTime()).isEqualTo(newEnd);
		assertThat(rescheduled.getChangeReason()).isEqualTo("Owner requested afternoon with vet 2");

		// Old slot occupancy is freed
		Optional<SlotOccupancy> oldOcc = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(oldOcc).isEmpty();

		// New slot occupancy is created
		Optional<SlotOccupancy> newOcc = this.slotOccupancies.findByVetIdAndStartTime(this.vet2.getId(), newStart);
		assertThat(newOcc).isPresent();
		assertThat(newOcc.get().getReferenceId()).isEqualTo(appt.getId());
	}

	@Test
	void shouldRejectRescheduleToOccupiedSlot() {
		LocalDate targetDate = LocalDate.now().plusDays(4);
		LocalDateTime slot1Start = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
		LocalDateTime slot1End = LocalDateTime.of(targetDate, LocalTime.of(10, 30));

		LocalDateTime slot2Start = LocalDateTime.of(targetDate, LocalTime.of(11, 0));
		LocalDateTime slot2End = LocalDateTime.of(targetDate, LocalTime.of(11, 30));

		Appointment appt1 = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				slot1Start, slot1End, "Appt 1");
		this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), slot2Start, slot2End,
				"Appt 2");

		assertThatThrownBy(() -> this.bookingService.rescheduleAppointment(appt1.getId(), this.vet.getId(), slot2Start,
				slot2End, "Attempt to take occupied slot"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("conflicts with an existing appointment");
	}

	@Test
	void shouldCancelByStaffAndRecordChangeReasonAndFreeSlot() {
		// AC-53: Staff cancel records reason
		LocalDate targetDate = LocalDate.now().plusDays(5);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(10, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "To cancel");

		Appointment cancelled = this.bookingService.cancelByStaff(appt.getId(), "Staff emergency schedule change");

		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(cancelled.getChangeReason()).isEqualTo("Staff emergency schedule change");

		// Slot is freed
		Optional<SlotOccupancy> occ = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occ).isEmpty();
	}

	@Test
	void shouldCompleteAppointmentAndCreateVisit() {
		// AC-54: Completion spawns a visit
		LocalDate targetDate = LocalDate.now().plusDays(5);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(11, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(11, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Vaccination follow-up");

		int initialVisits = this.pet.getVisits().size();

		Appointment completed = this.bookingService.completeAppointment(appt.getId());

		assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

		// Reload owner and check pet visits
		Owner reloadedOwner = this.owners.findById(this.owner.getId()).orElseThrow();
		Pet reloadedPet = reloadedOwner.getPet(this.pet.getId());
		assertThat(reloadedPet.getVisits().size()).isEqualTo(initialVisits + 1);

		Visit latestVisit = reloadedPet.getVisits()
			.stream()
			.filter(v -> v.getDescription().equals("Vaccination follow-up"))
			.findFirst()
			.orElse(null);
		assertThat(latestVisit).isNotNull();
		assertThat(latestVisit.getDate()).isEqualTo(targetDate);
	}

	@Test
	void shouldMarkNoShowAndNotCreateVisit() {
		// AC-55: No-show records no visit
		LocalDate targetDate = LocalDate.now().plusDays(5);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(14, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(14, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Dental cleaning");

		int initialVisits = this.pet.getVisits().size();

		Appointment noShow = this.bookingService.markNoShow(appt.getId());

		assertThat(noShow.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);

		Owner reloadedOwner = this.owners.findById(this.owner.getId()).orElseThrow();
		Pet reloadedPet = reloadedOwner.getPet(this.pet.getId());
		assertThat(reloadedPet.getVisits().size()).isEqualTo(initialVisits);
	}

	@Test
	void shouldCancelByOwnerBeforeStartAndFreeSlot() {
		// AC-56: Owner cancels own upcoming appointment before start
		LocalDate targetDate = LocalDate.now().plusDays(6);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(9, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(9, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Owner routine check");

		Appointment cancelled = this.bookingService.cancelByOwner(appt.getId(), this.owner.getId());

		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

		Optional<SlotOccupancy> occ = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occ).isEmpty();
	}

	@Test
	void shouldRejectCancelByOwnerAtOrAfterStart() {
		// AC-57: Owner cannot cancel an appointment at or after its start
		LocalDateTime pastStart = this.availabilityService.getNow().toLocalDateTime().minusHours(1);
		LocalDateTime pastEnd = pastStart.plusMinutes(30);

		Appointment appt = new Appointment();
		appt.setOwner(this.owner);
		appt.setPet(this.pet);
		appt.setVet(this.vet);
		appt.setStartTime(pastStart);
		appt.setEndTime(pastEnd);
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setReason("Past appointment");
		appt = this.appointments.saveAndFlush(appt);

		final Integer apptId = appt.getId();
		final Integer ownerId = this.owner.getId();
		assertThatThrownBy(() -> this.bookingService.cancelByOwner(apptId, ownerId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot cancel an appointment at or after its start time");

		// Verify it is not listed as an upcoming appointment
		List<Appointment> upcoming = this.bookingService.getUpcomingAppointmentsForOwner(this.owner.getId());
		assertThat(upcoming.stream().noneMatch(a -> a.getId().equals(apptId))).isTrue();
	}

	@Test
	void shouldRejectCancelByOwnerForAnotherOwnersAppointment() {
		// AC-58: Owner cannot access others' appointments
		LocalDate targetDate = LocalDate.now().plusDays(6);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(15, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(15, 30));

		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Owner 1 appt");

		Owner otherOwner = this.owners.findById(2).orElseThrow();

		final Integer apptId = appt.getId();
		final Integer otherOwnerId = otherOwner.getId();
		assertThatThrownBy(() -> this.bookingService.cancelByOwner(apptId, otherOwnerId))
			.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void shouldBookOnBehalfAndConfirmRequest() {
		// AC-50: Staff complete interpretation and book on behalf
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setRawText("Queued emergency request");
		request.setState(RequestState.STAFF_QUEUED);
		request.setQueueReason(QueueReason.EMERGENCY);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(13, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(13, 30));

		Appointment appt = this.bookingService.bookOnBehalf(request.getId(), this.vet.getId(), start, end,
				"Manual staff triage booking");

		assertThat(appt.getId()).isNotNull();
		assertThat(appt.getSchedulingRequest().getId()).isEqualTo(request.getId());
		assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);

		SchedulingRequest reloadedReq = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(reloadedReq.getState()).isEqualTo(RequestState.CONFIRMED);
	}

	@Test
	void getScheduleItemsForOwnerExcludesConfirmedRequests() {
		SchedulingRequest confirmed = new SchedulingRequest();
		confirmed.setOwner(this.owner);
		confirmed.setPet(this.pet);
		confirmed.setRawText("Already booked checkup");
		confirmed.setState(RequestState.CONFIRMED);
		confirmed = this.schedulingRequests.saveAndFlush(confirmed);

		List<OwnerScheduleItem> items = this.bookingService.getScheduleItemsForOwner(this.owner.getId());

		assertThat(requestIndex(items, confirmed.getId())).isEqualTo(-1);
		// A CONFIRMED request is represented by its appointment, not by a request row;
		// the
		// owner has no appointments here, so the schedule is empty.
		assertThat(items).isEmpty();
	}

	@Test
	void getScheduleItemsForOwnerIncludesActiveAndRecentClosedButExcludesOldClosed() {
		SchedulingRequest active = new SchedulingRequest();
		active.setOwner(this.owner);
		active.setPet(this.pet);
		active.setRawText("Need a slot soon");
		active.setState(RequestState.SLOT_HELD);
		active = this.schedulingRequests.saveAndFlush(active);

		SchedulingRequest recentClosed = new SchedulingRequest();
		recentClosed.setOwner(this.owner);
		recentClosed.setPet(this.pet);
		recentClosed.setRawText("Changed my mind");
		recentClosed.setState(RequestState.CANCELLED);
		recentClosed = this.schedulingRequests.saveAndFlush(recentClosed);

		SchedulingRequest oldClosed = new SchedulingRequest();
		oldClosed.setOwner(this.owner);
		oldClosed.setPet(this.pet);
		oldClosed.setRawText("Stale request");
		oldClosed.setState(RequestState.EXPIRED);
		oldClosed = this.schedulingRequests.saveAndFlush(oldClosed);

		// Back-date the terminal request beyond the recent-history window. A bulk update
		// bypasses the @PreUpdate callback that would otherwise reset updated_at to now.
		this.entityManager.createNativeQuery("UPDATE scheduling_requests SET updated_at = :ts WHERE id = :id")
			.setParameter("ts", Timestamp.from(Instant.now().minus(Duration.ofDays(30))))
			.setParameter("id", oldClosed.getId())
			.executeUpdate();
		this.entityManager.clear();

		List<OwnerScheduleItem> items = this.bookingService.getScheduleItemsForOwner(this.owner.getId());

		int activeIdx = requestIndex(items, active.getId());
		int recentClosedIdx = requestIndex(items, recentClosed.getId());
		int oldClosedIdx = requestIndex(items, oldClosed.getId());

		assertThat(activeIdx).isGreaterThanOrEqualTo(0);
		assertThat(recentClosedIdx).isGreaterThanOrEqualTo(0);
		assertThat(oldClosedIdx).isEqualTo(-1);
		// Active requests are ordered ahead of recently closed history.
		assertThat(activeIdx).isLessThan(recentClosedIdx);
	}

	@Test
	void getScheduleItemsForOwnerOrdersActionableActiveThenAppointmentsThenClosed() {
		Owner multiPetOwner = this.owners.findById(3).orElseThrow();
		Pet firstPet = multiPetOwner.getPet(3);
		Pet secondPet = multiPetOwner.getPet(4);

		SchedulingRequest actionable = new SchedulingRequest();
		actionable.setOwner(multiPetOwner);
		actionable.setPet(firstPet);
		actionable.setRawText("Slot offered");
		actionable.setState(RequestState.SLOT_HELD);
		actionable = this.schedulingRequests.saveAndFlush(actionable);

		SchedulingRequest activeNonActionable = new SchedulingRequest();
		activeNonActionable.setOwner(multiPetOwner);
		activeNonActionable.setPet(secondPet);
		activeNonActionable.setRawText("Analyzing");
		activeNonActionable.setState(RequestState.INTERPRETING);
		activeNonActionable = this.schedulingRequests.saveAndFlush(activeNonActionable);

		SchedulingRequest closed = new SchedulingRequest();
		closed.setOwner(multiPetOwner);
		closed.setPet(firstPet);
		closed.setRawText("Cancelled attempt");
		closed.setState(RequestState.CANCELLED);
		closed = this.schedulingRequests.saveAndFlush(closed);

		LocalDate targetDate = LocalDate.now().plusDays(3);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(9, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(9, 30));
		Appointment appt = this.bookingService.bookDirect(multiPetOwner.getId(), secondPet.getId(), this.vet.getId(),
				start, end, "Upcoming visit");

		List<OwnerScheduleItem> items = this.bookingService.getScheduleItemsForOwner(multiPetOwner.getId());

		int actionableIdx = requestIndex(items, actionable.getId());
		int activeIdx = requestIndex(items, activeNonActionable.getId());
		int apptIdx = appointmentIndex(items, appt.getId());
		int closedIdx = requestIndex(items, closed.getId());

		assertThat(actionableIdx).isGreaterThanOrEqualTo(0);
		assertThat(activeIdx).isGreaterThanOrEqualTo(0);
		assertThat(apptIdx).isGreaterThanOrEqualTo(0);
		assertThat(closedIdx).isGreaterThanOrEqualTo(0);

		// Order: actionable request, then other active request, then appointment, then
		// recently closed request.
		assertThat(actionableIdx).isLessThan(activeIdx);
		assertThat(activeIdx).isLessThan(apptIdx);
		assertThat(apptIdx).isLessThan(closedIdx);

		assertThat(items.get(actionableIdx).actionable()).isTrue();
		assertThat(items.get(activeIdx).actionable()).isFalse();
	}

	@Test
	void getScheduleItemsForOwnerMapsRequestAndAppointmentFields() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setRawText("Vaccines please");
		request.setState(RequestState.SLOT_HELD);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalDateTime start = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
		LocalDateTime end = LocalDateTime.of(targetDate, LocalTime.of(10, 30));
		Appointment appt = this.bookingService.bookDirect(this.owner.getId(), this.pet.getId(), this.vet.getId(), start,
				end, "Checkup");

		List<OwnerScheduleItem> items = this.bookingService.getScheduleItemsForOwner(this.owner.getId());

		OwnerScheduleItem requestItem = items.stream()
			.filter(it -> it.kind() == OwnerScheduleItem.Kind.REQUEST)
			.findFirst()
			.orElseThrow();
		assertThat(requestItem.id()).isEqualTo(request.getId());
		assertThat(requestItem.statusLabel()).isEqualTo("Slot offered — respond");
		assertThat(requestItem.badgeClass()).isEqualTo("bg-primary");
		assertThat(requestItem.actionable()).isTrue();
		assertThat(requestItem.when()).isNull();
		assertThat(requestItem.petName()).isEqualTo(this.pet.getName());
		assertThat(requestItem.vetOrSummary()).isEqualTo("Vaccines please");
		assertThat(requestItem.detailUrl()).isEqualTo("/scheduling/requests/" + request.getId());
		assertThat(requestItem.cancelUrl()).isEqualTo("/scheduling/requests/" + request.getId() + "/cancel");

		OwnerScheduleItem appointmentItem = items.stream()
			.filter(it -> it.kind() == OwnerScheduleItem.Kind.APPOINTMENT)
			.findFirst()
			.orElseThrow();
		assertThat(appointmentItem.id()).isEqualTo(appt.getId());
		assertThat(appointmentItem.statusLabel()).isEqualTo("Booked");
		assertThat(appointmentItem.badgeClass()).isEqualTo("bg-success");
		assertThat(appointmentItem.actionable()).isFalse();
		assertThat(appointmentItem.when()).isEqualTo(start);
		assertThat(appointmentItem.petName()).isEqualTo(this.pet.getName());
		assertThat(appointmentItem.vetOrSummary()).isEqualTo(this.vet.getFirstName() + " " + this.vet.getLastName());
		assertThat(appointmentItem.detailUrl()).isEqualTo("/my-appointments/" + appt.getId());
		assertThat(appointmentItem.cancelUrl()).isEqualTo("/my-appointments/" + appt.getId() + "/cancel");
	}

	private static int requestIndex(List<OwnerScheduleItem> items, Integer requestId) {
		for (int i = 0; i < items.size(); i++) {
			OwnerScheduleItem item = items.get(i);
			if (item.kind() == OwnerScheduleItem.Kind.REQUEST && requestId.equals(item.id())) {
				return i;
			}
		}
		return -1;
	}

	private static int appointmentIndex(List<OwnerScheduleItem> items, Integer appointmentId) {
		for (int i = 0; i < items.size(); i++) {
			OwnerScheduleItem item = items.get(i);
			if (item.kind() == OwnerScheduleItem.Kind.APPOINTMENT && appointmentId.equals(item.id())) {
				return i;
			}
		}
		return -1;
	}

}
