/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.request;

/** Scope selected by an owner when rejecting a suggested appointment. */
public enum RejectionScope {

	NOT_THIS_TIME,

	NOT_THIS_DAY,

	NOT_THIS_VET;

	public static RejectionScope parse(String value) {
		try {
			return RejectionScope.valueOf(value);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new IllegalArgumentException("Unknown rejection scope: " + value, ex);
		}
	}

}
