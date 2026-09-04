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

import java.util.List;

import org.springframework.samples.petclinic.scheduling.request.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.StaffQueueService.StaffQueueItem;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the staff scheduling queue (AC-84..86).
 */
@Controller
public class StaffQueueController {

	private final StaffQueueService staffQueueService;

	public StaffQueueController(StaffQueueService staffQueueService) {
		this.staffQueueService = staffQueueService;
	}

	@GetMapping("/staff/queue")
	public String queue(@RequestParam(name = "tab", required = false, defaultValue = "needs-staff") String tab,
			Model model) {
		String activeTab = ("all-open".equalsIgnoreCase(tab)) ? "all-open" : "needs-staff";
		model.addAttribute("activeTab", activeTab);

		List<StaffQueueItem> items;
		if ("all-open".equals(activeTab)) {
			items = this.staffQueueService.getAllOpenQueue();
		}
		else {
			items = this.staffQueueService.getNeedsStaffQueue();
		}
		model.addAttribute("queueItems", items);

		return "staff/queue";
	}

}
