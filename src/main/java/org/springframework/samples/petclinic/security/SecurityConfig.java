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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/resources/**", "/webjars/**", "/css/**", "/images/**", "/js/**", "/favicon.ico",
					"/error")
			.permitAll()
			.requestMatchers("/", "/oups", "/vets/**", "/vets.html", "/login", "/logout", "/change-password",
					"/change-password/**")
			.permitAll()
			.requestMatchers("/staff/**", "/owners/**")
			.hasRole("STAFF")
			.requestMatchers("/my-profile/**", "/my-pets/**", "/my-appointments/**", "/scheduling/**", "/schedule/**")
			.hasAnyRole("OWNER", "STAFF")
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
			.httpBasic(Customizer.withDefaults())
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/owners/*/pets/new"))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
			.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").permitAll());

		return http.build();
	}

}
