package org.springframework.samples.petclinic.availability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "clinic_policy")
public class ClinicPolicy {

	@Id
	private Integer id = 1;

	@Column(name = "zone_id", nullable = false, length = 64)
	private String zoneId = "Europe/Amsterdam";

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays = 90;

	@Column(name = "hold_duration_minutes", nullable = false)
	private int holdDurationMinutes = 10;

	@Column(name = "owner_notice_minutes", nullable = false)
	private int ownerNoticeMinutes = 120;

	@Column(name = "start_grid_minutes", nullable = false)
	private int startGridMinutes = 15;

	@Column(name = "clinic_phone", nullable = false, length = 32)
	private String clinicPhone = "555-0100";

	@Column(name = "contact_hours", nullable = false, length = 255)
	private String contactHours = "Mon-Fri 08:30-17:30";

	@Column(name = "urgent_care_guidance", nullable = false, columnDefinition = "TEXT")
	private String urgentCareGuidance = "If your pet has severe bleeding, difficulty breathing, or suspected poisoning, please call the clinic directly or visit the nearest emergency animal hospital immediately.";

	@OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<ClinicOperatingInterval> operatingIntervals = new ArrayList<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "clinic_allowed_durations", joinColumns = @JoinColumn(name = "policy_id"))
	@Column(name = "duration_minutes", nullable = false)
	private Set<Integer> allowedDurations = new HashSet<>();

	@OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<NamedPeriod> namedPeriods = new ArrayList<>();

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		if (this.updatedAt == null) {
			this.updatedAt = now;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public java.time.ZoneId getTimeZone() {
		return java.time.ZoneId.of(this.zoneId);
	}

	public static ClinicPolicy createDefaultPolicy() {
		ClinicPolicy policy = new ClinicPolicy();
		policy.setAllowedDurations(new HashSet<>(Set.of(15, 30, 45, 60)));
		return policy;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(int bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public int getHoldDurationMinutes() {
		return this.holdDurationMinutes;
	}

	public void setHoldDurationMinutes(int holdDurationMinutes) {
		this.holdDurationMinutes = holdDurationMinutes;
	}

	public int getOwnerNoticeMinutes() {
		return this.ownerNoticeMinutes;
	}

	public void setOwnerNoticeMinutes(int ownerNoticeMinutes) {
		this.ownerNoticeMinutes = ownerNoticeMinutes;
	}

	public int getStartGridMinutes() {
		return this.startGridMinutes;
	}

	public void setStartGridMinutes(int startGridMinutes) {
		this.startGridMinutes = startGridMinutes;
	}

	public String getClinicPhone() {
		return this.clinicPhone;
	}

	public void setClinicPhone(String clinicPhone) {
		this.clinicPhone = clinicPhone;
	}

	public String getContactHours() {
		return this.contactHours;
	}

	public void setContactHours(String contactHours) {
		this.contactHours = contactHours;
	}

	public String getUrgentCareGuidance() {
		return this.urgentCareGuidance;
	}

	public void setUrgentCareGuidance(String urgentCareGuidance) {
		this.urgentCareGuidance = urgentCareGuidance;
	}

	public List<ClinicOperatingInterval> getOperatingIntervals() {
		return this.operatingIntervals;
	}

	public void setOperatingIntervals(List<ClinicOperatingInterval> operatingIntervals) {
		this.operatingIntervals = operatingIntervals;
	}

	public void addOperatingInterval(ClinicOperatingInterval interval) {
		this.operatingIntervals.add(interval);
		interval.setPolicy(this);
	}

	public Set<Integer> getAllowedDurations() {
		return this.allowedDurations;
	}

	public void setAllowedDurations(Set<Integer> allowedDurations) {
		this.allowedDurations = allowedDurations;
	}

	public List<NamedPeriod> getNamedPeriods() {
		return this.namedPeriods;
	}

	public void setNamedPeriods(List<NamedPeriod> namedPeriods) {
		this.namedPeriods = namedPeriods;
	}

	public void addNamedPeriod(NamedPeriod namedPeriod) {
		this.namedPeriods.add(namedPeriod);
		namedPeriod.setPolicy(this);
	}

	public Long getVersion() {
		return this.version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
