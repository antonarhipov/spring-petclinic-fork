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

package org.springframework.samples.petclinic.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Authentication success handler redirecting users by their role: Owner ->
 * /my/appointments, Staff -> /staff/queue.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			String role = authority.getAuthority();
			if ("ROLE_OWNER".equals(role)) {
				response.sendRedirect(request.getContextPath() + "/my/appointments");
				return;
			}
			if ("ROLE_STAFF".equals(role)) {
				response.sendRedirect(request.getContextPath() + "/staff/queue");
				return;
			}
		}
		response.sendRedirect(request.getContextPath() + "/");
	}

}
