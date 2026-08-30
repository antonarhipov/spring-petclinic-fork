package org.springframework.samples.petclinic.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AccountRepository accounts) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/", "/login", "/oups", "/resources/**", "/webjars/**", "/css/**", "/images/**", "/error")
			.permitAll()
			.requestMatchers("/owners/**", "/pets/**", "/vets/**", "/vets.html")
			.hasRole("STAFF")
			.requestMatchers("/api/owner/**")
			.hasRole("OWNER")
			.requestMatchers("/owner/**")
			.hasRole("OWNER")
			.requestMatchers("/staff/**")
			.hasRole("STAFF")
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginPage("/login").permitAll())
			.logout(Customizer.withDefaults())
			.csrf(Customizer.withDefaults())
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
			.sessionManagement(session -> session.sessionFixation().changeSessionId())
			.addFilterAfter(new TemporaryPasswordRestrictionFilter(accounts),
					UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

}
