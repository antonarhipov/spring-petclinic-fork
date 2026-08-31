package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "availability_windows")
public class AvailabilityWindow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_revision_id", nullable = false)
	private WorkflowRevision workflowRevision;

	@Enumerated(EnumType.STRING)
	@Column(name = "classification", nullable = false, length = 32)
	private WindowClassification classification;

	@Enumerated(EnumType.STRING)
	@Column(name = "shape", nullable = false, length = 32)
	private WindowShape shape;

	@Column(name = "local_date")
	private LocalDate localDate;

	@Column(name = "range_start")
	private LocalDate rangeStart;

	@Column(name = "range_end")
	private LocalDate rangeEnd;

	@Column(name = "weekdays", length = 128)
	private String weekdays;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
	private String sourceText;

	@Column(name = "resolution_note", columnDefinition = "TEXT")
	private String resolutionNote;

	public AvailabilityWindow() {
	}

	public AvailabilityWindow(WorkflowRevision workflowRevision, WindowClassification classification, WindowShape shape,
			LocalDate localDate, LocalDate rangeStart, LocalDate rangeEnd, String weekdays, LocalTime startTime,
			LocalTime endTime, String sourceText, String resolutionNote) {
		this.workflowRevision = workflowRevision;
		this.classification = Objects.requireNonNull(classification, "classification must not be null");
		this.shape = Objects.requireNonNull(shape, "shape must not be null");
		this.localDate = localDate;
		this.rangeStart = rangeStart;
		this.rangeEnd = rangeEnd;
		this.weekdays = weekdays;
		this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
		this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
		this.sourceText = Objects.requireNonNull(sourceText, "sourceText must not be null");
		this.resolutionNote = resolutionNote;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public WorkflowRevision getWorkflowRevision() {
		return this.workflowRevision;
	}

	public void setWorkflowRevision(WorkflowRevision workflowRevision) {
		this.workflowRevision = workflowRevision;
	}

	public WindowClassification getClassification() {
		return this.classification;
	}

	public void setClassification(WindowClassification classification) {
		this.classification = classification;
	}

	public WindowShape getShape() {
		return this.shape;
	}

	public void setShape(WindowShape shape) {
		this.shape = shape;
	}

	public LocalDate getLocalDate() {
		return this.localDate;
	}

	public void setLocalDate(LocalDate localDate) {
		this.localDate = localDate;
	}

	public LocalDate getRangeStart() {
		return this.rangeStart;
	}

	public void setRangeStart(LocalDate rangeStart) {
		this.rangeStart = rangeStart;
	}

	public LocalDate getRangeEnd() {
		return this.rangeEnd;
	}

	public void setRangeEnd(LocalDate rangeEnd) {
		this.rangeEnd = rangeEnd;
	}

	public String getWeekdays() {
		return this.weekdays;
	}

	public void setWeekdays(String weekdays) {
		this.weekdays = weekdays;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public String getSourceText() {
		return this.sourceText;
	}

	public void setSourceText(String sourceText) {
		this.sourceText = sourceText;
	}

	public String getResolutionNote() {
		return this.resolutionNote;
	}

	public void setResolutionNote(String resolutionNote) {
		this.resolutionNote = resolutionNote;
	}

}
