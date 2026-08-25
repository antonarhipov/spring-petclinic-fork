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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

	private final ObjectProvider<UserAccountRepository> userAccountRepositoryProvider;

	public PasswordChangeInterceptor(ObjectProvider<UserAccountRepository> userAccountRepositoryProvider) {
		this.userAccountRepositoryProvider = userAccountRepositoryProvider;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String uri = request.getRequestURI();

		if (uri.startsWith("/change-password") || uri.startsWith("/logout") || uri.startsWith("/login")
				|| uri.startsWith("/resources/") || uri.startsWith("/webjars/") || uri.startsWith("/css/")
				|| uri.startsWith("/images/") || uri.startsWith("/js/") || uri.equals("/favicon.ico")
				|| uri.startsWith("/error")) {
			return true;
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
			UserAccountRepository repository = this.userAccountRepositoryProvider.getIfAvailable();
			if (repository != null) {
				String username = auth.getName();
				UserAccount account = repository.findByUsername(username).orElse(null);
				if (account != null && account.isMustChangePassword()) {
					response.sendRedirect("/change-password");
					return false;
				}
			}
		}

		return true;
	}

}
