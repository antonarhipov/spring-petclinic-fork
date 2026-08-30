package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecord;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService.WindowEdit;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class OwnerInterpretationController {

	public static class FieldIssue {

		private final String field;

		private final String code;

		public FieldIssue(String field, String code) {
			this.field = field;
			this.code = code;
		}

		public String getField() {
			return this.field;
		}

		public String getCode() {
			return this.code;
		}

	}

	private final CurrentOwnerAccount currentOwner;

	private final RequestWorkflowService workflow;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	private final InterpretationRecordRepository interpretations;

	public OwnerInterpretationController(CurrentOwnerAccount currentOwner, RequestWorkflowService workflow,
			RequestRevisionRepository revisions, RequestWindowRepository windows,
			InterpretationRecordRepository interpretations) {
		this.currentOwner = currentOwner;
		this.workflow = workflow;
		this.revisions = revisions;
		this.windows = windows;
		this.interpretations = interpretations;
	}

	@GetMapping("/owner/scheduling-requests/{id}/interpretation")
	public String review(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		InterpretationReviewForm form = new InterpretationReviewForm();
		form.setExpectedVersion(request.getVersion());
		form.setVisitReason(revision.getVisitReason());
		form.setDurationMinutes(revision.getDurationMinutes());
		form.setPreferredVeterinarianId(revision.getPreferredVeterinarianId());
		List<FieldIssue> fieldIssues = loadFieldIssues(revision);
		List<String> topIssues = loadTopIssues(revision);
		boolean hasIssues = !fieldIssues.isEmpty() || !topIssues.isEmpty();
		model.addAttribute("request", request);
		model.addAttribute("revision", revision);
		model.addAttribute("windows", this.windows.findByRequestRevisionId(revision.getId()));
		model.addAttribute("form", form);
		model.addAttribute("fieldIssues", fieldIssues);
		model.addAttribute("topIssues", topIssues);
		model.addAttribute("hasIssues", hasIssues);
		model.addAttribute("confirmEnabled", request.getState() == RequestState.INTERPRETATION_REVIEW && !hasIssues);
		return "scheduling/owner/interpretation-review";
	}

	@PostMapping("/owner/scheduling-requests/{id}/interpretation")
	public String save(@PathVariable Long id, @ModelAttribute InterpretationReviewForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.saveOwnerEdits(id, account.getOwnerId(), form.getExpectedVersion(), form.getVisitReason(),
				form.getDurationMinutes(), form.getPreferredVeterinarianId(), toEdits(form.getAllowedWindows()),
				toEdits(form.getPreferredWindows()), toEdits(form.getExcludedWindows()));
		return "redirect:/owner/scheduling-requests/" + id + "/interpretation";
	}

	@PostMapping("/owner/scheduling-requests/{id}/interpretation/confirm")
	public String confirm(@PathVariable Long id, @ModelAttribute InterpretationReviewForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.confirm(id, account.getOwnerId(), account.getId(), form.getExpectedVersion());
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		if (request.getState() != RequestState.READY_FOR_SUGGESTION) {
			return "redirect:/owner/scheduling-requests/" + id + "/interpretation";
		}
		return "redirect:/owner/scheduling-requests/" + id + "/suggestion";
	}

	private static List<WindowEdit> toEdits(List<InterpretationReviewForm.WindowRow> rows) {
		return rows.stream()
			.map(row -> new WindowEdit(row.getStartDate(), row.getStartTime(), row.getEndDate(), row.getEndTime()))
			.toList();
	}

	private List<FieldIssue> loadFieldIssues(RequestRevision revision) {
		InterpretationRecord record = loadInterpretation(revision);
		if (record == null || record.getUncertaintiesJson() == null) {
			return List.of();
		}
		try {
			List<Map<String, String>> raw = new ObjectMapper().readValue(record.getUncertaintiesJson(),
					new TypeReference<List<Map<String, String>>>() {
					});
			return raw.stream().map(entry -> new FieldIssue(entry.get("fieldPath"), entry.get("code"))).toList();
		}
		catch (Exception ex) {
			return List.of();
		}
	}

	private List<String> loadTopIssues(RequestRevision revision) {
		InterpretationRecord record = loadInterpretation(revision);
		if (record == null || record.getValidationIssuesJson() == null) {
			return List.of();
		}
		try {
			return new ObjectMapper().readValue(record.getValidationIssuesJson(), new TypeReference<List<String>>() {
			});
		}
		catch (Exception ex) {
			return List.of();
		}
	}

	private InterpretationRecord loadInterpretation(RequestRevision revision) {
		if (revision.getInterpretationId() == null) {
			return null;
		}
		return this.interpretations.findById(revision.getInterpretationId()).orElse(null);
	}

}
