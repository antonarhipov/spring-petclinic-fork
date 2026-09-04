/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

/** Owner-editable text fields for an existing scheduling request. */
public class OwnerRequestEditForm {

	private String reasonText;

	private String availabilityText;

	public static OwnerRequestEditForm from(SchedulingRequest request) {
		OwnerRequestEditForm form = new OwnerRequestEditForm();
		form.setReasonText(request.getReasonText());
		form.setAvailabilityText(request.getAvailabilityText());
		return form;
	}

	public String getReasonText() {
		return this.reasonText;
	}

	public void setReasonText(String reasonText) {
		this.reasonText = reasonText;
	}

	public String getAvailabilityText() {
		return this.availabilityText;
	}

	public void setAvailabilityText(String availabilityText) {
		this.availabilityText = availabilityText;
	}

}
