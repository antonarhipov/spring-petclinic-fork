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

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current authentication state to every view so the shared navigation
 * fragment can render role-aware entry points into the scheduling feature.
 */
@ControllerAdvice
public class GlobalModelAttributes {

	@ModelAttribute
	public void addNavigationAttributes(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean authenticated = authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
		boolean staff = false;
		boolean owner = false;
		String username = null;
		if (authenticated) {
			username = authentication.getName();
			for (GrantedAuthority authority : authentication.getAuthorities()) {
				if ("ROLE_STAFF".equals(authority.getAuthority())) {
					staff = true;
				}
				else if ("ROLE_OWNER".equals(authority.getAuthority())) {
					owner = true;
				}
			}
		}
		model.addAttribute("currentUserAuthenticated", authenticated);
		model.addAttribute("currentUserIsStaff", staff);
		model.addAttribute("currentUserIsOwner", owner);
		model.addAttribute("currentUsername", username);
	}

}
