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

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Baseline Spring Security configuration enforcing the authorization matrix per RULE-12.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private final AuthenticationSuccessHandler authenticationSuccessHandler;

	public SecurityConfiguration(AuthenticationSuccessHandler authenticationSuccessHandler) {
		this.authenticationSuccessHandler = authenticationSuccessHandler;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.csrf(Customizer.withDefaults())
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/login")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/login")
				.permitAll()
				.requestMatchers("/error", "/resources/**", "/webjars/**", "/favicon.ico", "/*.css")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/403")
				.authenticated()
				.requestMatchers("/logout", "/")
				.authenticated()
				.requestMatchers("/oups", "/owners/**", "/vets", "/vets.html", "/vets/**", "/staff/**", "/actuator/**")
				.hasRole("STAFF")
				.requestMatchers("/my/requests/**")
				.hasRole("OWNER")
				.requestMatchers("/my/**")
				.hasRole("OWNER")
				.anyRequest()
				.authenticated())
			.formLogin(form -> form.loginPage("/login")
				.loginProcessingUrl("/login")
				.successHandler(this.authenticationSuccessHandler)
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout.disable())
			.exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				request.getRequestDispatcher("/403").forward(request, response);
			}));

		return http.build();
	}

}
