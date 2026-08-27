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
package org.springframework.samples.petclinic.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getPrincipal())) {
			boolean mustChange = false;
			Object principal = authentication.getPrincipal();
			if (principal instanceof AppUserDetails userDetails) {
				mustChange = userDetails.isMustChangePassword();
			}

			if (mustChange) {
				String uri = request.getRequestURI();
				String contextPath = request.getContextPath();
				String relativePath = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

				boolean allowed = relativePath.startsWith("/change-password") || relativePath.startsWith("/logout")
						|| relativePath.startsWith("/resources/") || relativePath.startsWith("/webjars/")
						|| relativePath.startsWith("/css/") || relativePath.startsWith("/images/")
						|| relativePath.startsWith("/favicon.ico") || relativePath.startsWith("/error")
						|| relativePath.startsWith("/oups");

				if (!allowed) {
					response.sendRedirect(contextPath + "/change-password");
					return;
				}
			}
		}

		filterChain.doFilter(request, response);
	}

}
