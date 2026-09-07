package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "interpretations")
public class Interpretation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "understood", nullable = false)
	private boolean understood;

	@Enumerated(EnumType.STRING)
	@Column(name = "care_type", length = 16)
	private CareType careType;

	@Column(name = "specialty", length = 80)
	private String specialty;

	@Column(name = "specialty_label", length = 255)
	private String specialtyLabel;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "preferred_vet_id")
	private Vet preferredVet;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin", nullable = false, length = 16)
	private InterpretationOrigin origin;

	@Lob
	@Column(name = "raw_json")
	private String rawJson;

	@Column(name = "model_tag", length = 120)
	private String modelTag;

	@Column(name = "prompt_version", length = 80)
	private String promptVersion;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@Column(name = "created_time", nullable = false)
	private LocalTime createdTime;

	@OneToMany(mappedBy = "interpretation", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<InterpretationWindow> windows = new ArrayList<>();

	protected Interpretation() {
	}

	public Interpretation(boolean understood, CareType careType, String specialty, String specialtyLabel,
			Integer durationMinutes, Vet preferredVet, InterpretationOrigin origin, String rawJson, String modelTag,
			String promptVersion, LocalDate createdDate, LocalTime createdTime) {
		this.understood = understood;
		this.careType = careType;
		this.specialty = specialty;
		this.specialtyLabel = specialtyLabel;
		this.durationMinutes = durationMinutes;
		this.preferredVet = preferredVet;
		this.origin = origin;
		this.rawJson = rawJson;
		this.modelTag = modelTag;
		this.promptVersion = promptVersion;
		this.createdDate = createdDate;
		this.createdTime = createdTime;
	}

	void attachTo(SchedulingRequest request) {
		this.request = request;
	}

	public void addWindow(InterpretationWindow window) {
		window.attachTo(this);
		this.windows.add(window);
	}

	public boolean isOtherSpecialty() {
		return "OTHER".equals(this.specialty);
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public boolean isUnderstood() {
		return this.understood;
	}

	public CareType getCareType() {
		return this.careType;
	}

	public String getSpecialty() {
		return this.specialty;
	}

	public String getSpecialtyLabel() {
		return this.specialtyLabel;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public Vet getPreferredVet() {
		return this.preferredVet;
	}

	public InterpretationOrigin getOrigin() {
		return this.origin;
	}

	public String getRawJson() {
		return this.rawJson;
	}

	public String getModelTag() {
		return this.modelTag;
	}

	public String getPromptVersion() {
		return this.promptVersion;
	}

	public LocalDate getCreatedDate() {
		return this.createdDate;
	}

	public LocalTime getCreatedTime() {
		return this.createdTime;
	}

	public List<InterpretationWindow> getWindows() {
		return Collections.unmodifiableList(this.windows);
	}

	public List<InterpretationWindow> getPreferredWindows() {
		return this.windows.stream().filter(window -> "PREFERRED".equals(window.getWindowKind())).toList();
	}

	public List<InterpretationWindow> getAllowedWindows() {
		return this.windows.stream().filter(window -> "ALLOWED".equals(window.getWindowKind())).toList();
	}

	public List<InterpretationWindow> getExcludedWindows() {
		return this.windows.stream().filter(window -> "EXCLUDED".equals(window.getWindowKind())).toList();
	}

	public CareType getEffectiveCareType() {
		return this.careType != null ? this.careType : CareType.GENERAL;
	}

	public int getEffectiveDurationMinutes(int defaultDuration) {
		return this.durationMinutes != null ? this.durationMinutes : defaultDuration;
	}

}
