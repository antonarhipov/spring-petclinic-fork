package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;

public class NotificationDto {

	private Integer id;

	private String titleKey;

	private String messageKey;

	private String messageParams;

	private String targetUrl;

	private boolean read;

	private boolean authorized;

	private Instant createdAt;

	public NotificationDto() {
	}

	public NotificationDto(Integer id, String titleKey, String messageKey, String messageParams, String targetUrl,
			boolean read, boolean authorized, Instant createdAt) {
		this.id = id;
		this.titleKey = titleKey;
		this.messageKey = messageKey;
		this.messageParams = messageParams;
		this.targetUrl = targetUrl;
		this.read = read;
		this.authorized = authorized;
		this.createdAt = createdAt;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getTitleKey() {
		return titleKey;
	}

	public void setTitleKey(String titleKey) {
		this.titleKey = titleKey;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageParams() {
		return messageParams;
	}

	public void setMessageParams(String messageParams) {
		this.messageParams = messageParams;
	}

	public String getTargetUrl() {
		return targetUrl;
	}

	public void setTargetUrl(String targetUrl) {
		this.targetUrl = targetUrl;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public boolean isAuthorized() {
		return authorized;
	}

	public void setAuthorized(boolean authorized) {
		this.authorized = authorized;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
