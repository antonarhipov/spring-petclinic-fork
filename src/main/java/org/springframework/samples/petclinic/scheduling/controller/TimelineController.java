package org.springframework.samples.petclinic.scheduling.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.dto.AuditRecordDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AuditRecord;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class TimelineController {

	private final AuditService auditService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final ReservationRepository reservationRepository;

	private final AppointmentRepository appointmentRepository;

	public TimelineController(AuditService auditService, SchedulingRequestRepository schedulingRequestRepository,
			ReservationRepository reservationRepository, AppointmentRepository appointmentRepository) {
		this.auditService = auditService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.reservationRepository = reservationRepository;
		this.appointmentRepository = appointmentRepository;
	}

	@GetMapping("/scheduling/requests/{id}/timeline")
	public String requestTimeline(@PathVariable("id") Integer id, @AuthenticationPrincipal UserPrincipal userPrincipal,
			Model model) {
		if (userPrincipal == null) {
			return "redirect:/login";
		}

		SchedulingRequest request;
		if (userPrincipal.getRole() == AccountRole.STAFF) {
			request = schedulingRequestRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Request not found"));
		}
		else {
			Integer ownerId = userPrincipal.getOwnerId();
			if (ownerId == null) {
				throw new ResourceNotFoundException("Request not found");
			}
			request = schedulingRequestRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Request not found"));
		}

		List<Reservation> reservations = reservationRepository.findByRequestId(id);
		List<Integer> reservationIds = reservations.stream().map(Reservation::getId).toList();

		Optional<Appointment> appointmentOpt = appointmentRepository.findByRequestId(id);
		Integer appointmentId = appointmentOpt.map(Appointment::getId).orElse(null);

		List<AuditRecord> records = auditService.findTimelineForRequest(id, reservationIds, appointmentId);
		List<AuditRecordDto> dtos = records.stream()
			.map(r -> new AuditRecordDto(r.getId(), r.getEventType(), r.getActorUsername(), r.getActorRole(),
					r.getTargetEntityType(), r.getTargetEntityId(), r.getReasonCode(), r.getOccurredAt(),
					r.getMetadataJson()))
			.toList();

		model.addAttribute("requestId", id);
		model.addAttribute("request", request);
		model.addAttribute("auditRecords", dtos);
		return "scheduling/requestTimeline";
	}

	@GetMapping("/scheduling/appointments/{id}/timeline")
	public String appointmentTimeline(@PathVariable("id") Integer id,
			@AuthenticationPrincipal UserPrincipal userPrincipal, Model model) {
		if (userPrincipal == null) {
			return "redirect:/login";
		}

		Appointment appointment;
		if (userPrincipal.getRole() == AccountRole.STAFF) {
			appointment = appointmentRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
		}
		else {
			Integer ownerId = userPrincipal.getOwnerId();
			if (ownerId == null) {
				throw new ResourceNotFoundException("Appointment not found");
			}
			appointment = appointmentRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
		}

		Integer requestId = appointment.getRequest() != null ? appointment.getRequest().getId() : null;
		List<Integer> reservationIds = List.of();
		if (requestId != null) {
			List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
			reservationIds = reservations.stream().map(Reservation::getId).toList();
		}

		List<AuditRecord> records = (requestId != null)
				? auditService.findTimelineForRequest(requestId, reservationIds, id)
				: auditService.findByTarget("APPOINTMENT", String.valueOf(id));

		List<AuditRecordDto> dtos = records.stream()
			.map(r -> new AuditRecordDto(r.getId(), r.getEventType(), r.getActorUsername(), r.getActorRole(),
					r.getTargetEntityType(), r.getTargetEntityId(), r.getReasonCode(), r.getOccurredAt(),
					r.getMetadataJson()))
			.toList();

		model.addAttribute("appointmentId", id);
		model.addAttribute("appointment", appointment);
		model.addAttribute("auditRecords", dtos);
		return "scheduling/appointmentTimeline";
	}

}
