package org.springframework.samples.petclinic.scheduling;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentSource;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueuePriority;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchedulingUiIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private PetRepository pets;

	@Autowired
	private VetRepository vets;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private StaffQueueRepository queue;

	@Autowired
	private RequestRevisionRepository revisions;

	@Autowired
	private AppointmentOfferRepository offers;

	@Autowired
	private SchedulingAuditService audit;

	@Test
	void ownerDashboardShowsAppointmentAndRequestCancellationActions() throws Exception {
		Pet pet = this.pets.findById(1).orElseThrow();
		SchedulingRequest request = this.requests.saveAndFlush(new SchedulingRequest(pet, false));
		Appointment appointment = this.appointments.saveAndFlush(
				new Appointment(pet, this.vets.findById(1).orElseThrow(), Instant.parse("2029-08-28T10:00:00Z"), 30,
						AppointmentSource.STAFF_OPERATIONAL, null, null, null, null));

		this.mvc.perform(get("/my/appointments").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/my/appointments/" + appointment.getId() + "/cancel")))
			.andExpect(content().string(containsString("/my/scheduling/requests/" + request.getId() + "/withdraw")))
			.andExpect(content().string(containsString("Cancel appointment")))
			.andExpect(content().string(containsString("Cancel request")));
	}

	@Test
	void staffQueueShowsTheRequestingOwnerAndPet() throws Exception {
		SchedulingRequest request = this.requests
			.saveAndFlush(new SchedulingRequest(this.pets.findById(1).orElseThrow(), false));
		StaffQueueItem item = this.queue.saveAndFlush(new StaffQueueItem(request, QueuePriority.STANDARD));

		this.mvc.perform(get("/staff/queue").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("George Franklin")))
			.andExpect(content().string(containsString("Leo")));

		this.mvc.perform(get("/staff/queue/" + item.getId()).with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Requested by")))
			.andExpect(content().string(containsString("George Franklin")))
			.andExpect(content().string(containsString("6085551023")));
	}

	@Test
	void staffCanOpenVeterinarianScheduleManagement() throws Exception {
		this.mvc.perform(get("/staff/availability").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Vet schedules")))
			.andExpect(content().string(containsString("/staff/veterinarians/1/availability/shifts")))
			.andExpect(content().string(containsString("Weekly shifts")))
			.andExpect(content().string(containsString("Clinic closures")));
	}

	@Test
	void ownerRequestListShowsMessageInterpretationProposalAndEventLog() throws Exception {
		Pet pet = this.pets.findById(1).orElseThrow();
		SchedulingRequest request = this.requests.save(new SchedulingRequest(pet, false));
		RequestRevision revision = this.revisions
			.save(new RequestRevision(request, 1, "Leo needs an annual checkup next week", true,
					Instant.parse("2029-08-20T10:00:00Z"), "request-details-test"));
		revision.applyInterpretation("raw-system-output", "ollama", "Annual checkup", 30, "GENERAL", "dentistry", null,
				"STANDARD");
		request.setCurrentRevision(revision);
		request.moveTo(SchedulingRequestState.OFFER_HELD);
		this.requests.flush();
		this.offers.saveAndFlush(new AppointmentOffer(revision, this.vets.findById(1).orElseThrow(),
				Instant.parse("2029-08-28T10:00:00Z"), 30, Instant.parse("2029-08-20T10:01:00Z"),
				Instant.parse("2029-08-20T10:11:00Z"), "Matches the request"));
		this.audit.record(null, "request-details-test", AuditAction.REQUEST_SUBMITTED, "request", request.getId(), null,
				"INTERPRETATION_REVIEW", null);

		this.mvc.perform(get("/my/appointments").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leo needs an annual checkup next week")))
			.andExpect(content().string(containsString("Annual checkup")))
			.andExpect(content().string(containsString("raw-system-output")))
			.andExpect(content().string(containsString("Tuesday, August 28, 2029")))
			.andExpect(content().string(containsString("REQUEST_SUBMITTED")));
	}

	@Test
	void staffCalendarAndDetailsShowRelationsRequestEventsAndManualActions() throws Exception {
		Pet pet = this.pets.findById(1).orElseThrow();
		SchedulingRequest request = this.requests.save(new SchedulingRequest(pet, false));
		RequestRevision revision = this.revisions
			.save(new RequestRevision(request, 1, "Leo needs a follow-up examination", true,
					Instant.parse("2029-08-20T10:00:00Z"), "staff-details-test"));
		revision.applyInterpretation("staff-visible-interpretation", "ollama", "Follow-up examination", 30, "GENERAL",
				null, null, "STANDARD");
		request.setCurrentRevision(revision);
		request.moveTo(SchedulingRequestState.CONFIRMED);
		this.requests.flush();
		Appointment appointment = this.appointments.saveAndFlush(
				new Appointment(pet, this.vets.findById(1).orElseThrow(), Instant.parse("2029-08-28T10:00:00Z"), 30,
						AppointmentSource.OWNER_OFFER, request, revision, null, "Confirmed by phone"));
		this.audit.record(null, "staff-details-test", AuditAction.REQUEST_SUBMITTED, "request", request.getId(), null,
				"INTERPRETATION_REVIEW", null);
		this.audit.record(null, null, AuditAction.APPOINTMENT_BOOKED, "appointment", appointment.getId(), null,
				"CONFIRMED", "Owner confirmed");

		this.mvc.perform(get("/staff/calendar").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("George Franklin")))
			.andExpect(content().string(containsString("Leo")))
			.andExpect(content().string(containsString("James Carter")));

		this.mvc.perform(get("/staff/appointments/" + appointment.getId()).with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leo needs a follow-up examination")))
			.andExpect(content().string(containsString("staff-visible-interpretation")))
			.andExpect(content().string(containsString("APPOINTMENT_BOOKED")))
			.andExpect(content().string(containsString("/reschedule")))
			.andExpect(content().string(containsString("/cancel")));

		this.mvc
			.perform(post("/staff/appointments/" + appointment.getId() + "/reschedule").with(csrf())
				.with(user("admin").roles("STAFF"))
				.param("vetId", "1")
				.param("startAt", "2030-01-02T10:00")
				.param("reason", "Owner requested another time"))
			.andExpect(status().is3xxRedirection());
		assertThat(appointment.getStartAt()).isEqualTo(Instant.parse("2030-01-02T09:00:00Z"));

		this.mvc
			.perform(post("/staff/appointments/" + appointment.getId() + "/cancel").with(csrf())
				.with(user("admin").roles("STAFF"))
				.param("reason", "Owner no longer needs the appointment"))
			.andExpect(status().is3xxRedirection());
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
	}

	@Test
	void ownerCancellationClosesAConfirmedSchedulingRequest() throws Exception {
		Pet pet = this.pets.findById(1).orElseThrow();
		SchedulingRequest request = this.requests.saveAndFlush(new SchedulingRequest(pet, false));
		request.moveTo(SchedulingRequestState.CONFIRMED);
		Appointment appointment = this.appointments.saveAndFlush(
				new Appointment(pet, this.vets.findById(1).orElseThrow(), Instant.parse("2029-08-28T10:00:00Z"), 30,
						AppointmentSource.OWNER_OFFER, request, null, null, null));

		this.mvc
			.perform(post("/my/appointments/" + appointment.getId() + "/cancel").with(csrf())
				.with(user("george").roles("OWNER")))
			.andExpect(status().is3xxRedirection());

		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(request.getState()).isEqualTo(SchedulingRequestState.CLOSED);
	}

	@Test
	void cancellingAStaffHandledRequestClosesItsQueueItem() throws Exception {
		SchedulingRequest request = this.requests
			.saveAndFlush(new SchedulingRequest(this.pets.findById(1).orElseThrow(), false));
		RequestRevision revision = this.revisions
			.saveAndFlush(new RequestRevision(request, 1, "Routine visit next week", false, null, "test-correlation"));
		request.setCurrentRevision(revision);
		request.moveTo(SchedulingRequestState.STAFF_HANDLING);
		StaffQueueItem item = this.queue.saveAndFlush(new StaffQueueItem(request, QueuePriority.STANDARD));

		this.mvc
			.perform(post("/my/scheduling/requests/" + request.getId() + "/withdraw").with(csrf())
				.with(user("george").roles("OWNER")))
			.andExpect(status().is3xxRedirection());

		assertThat(request.getState()).isEqualTo(SchedulingRequestState.CLOSED);
		assertThat(item.getState()).isEqualTo(QueueState.CLOSED);
	}

}
