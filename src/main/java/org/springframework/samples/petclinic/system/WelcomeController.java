/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.system;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class WelcomeController {

	@GetMapping("/")
	public String welcome(HttpServletRequest request) {
		if (request.isUserInRole("OWNER") || request.isUserInRole("ROLE_OWNER")) {
			return "redirect:/my/appointments";
		}
		if (request.isUserInRole("STAFF") || request.isUserInRole("ROLE_STAFF")) {
			return "redirect:/staff/queue";
		}
		if (request.getUserPrincipal() instanceof Authentication auth) {
			for (GrantedAuthority authority : auth.getAuthorities()) {
				if ("ROLE_OWNER".equals(authority.getAuthority())) {
					return "redirect:/my/appointments";
				}
				if ("ROLE_STAFF".equals(authority.getAuthority())) {
					return "redirect:/staff/queue";
				}
			}
		}
		return "redirect:/login";
	}

}
