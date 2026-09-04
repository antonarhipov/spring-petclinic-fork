/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RouteInventoryTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void everyCompletedRouteResolvesIncludingAsBuiltPhase() throws IOException {
		PlanGuardSupport.Plan plan = PlanGuardSupport.readPlan();
		Set<String> mappings = applicationMappings();
		String securitySurface = Files.readString(PlanGuardSupport.RULES);
		for (PlanGuardSupport.RouteSpec route : plan.routes()) {
			if (plan.completed().contains(route.ownerTask())) {
				assertRoute(route, mappings, securitySurface);
			}
		}
	}

	@Test
	void unmappedRouteFixtureFails() throws IOException {
		PlanGuardSupport.RouteSpec broken = new PlanGuardSupport.RouteSpec("GET", "/missing-plan-route", "task-0.1");
		assertThatThrownBy(() -> assertRoute(broken, applicationMappings(), Files.readString(PlanGuardSupport.RULES)))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("GET /missing-plan-route");
	}

	private Set<String> applicationMappings() {
		Set<String> mappings = new LinkedHashSet<>();
		for (RequestMappingInfo info : this.handlerMapping.getHandlerMethods().keySet()) {
			Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition()
				.getMethods();
			for (String pattern : info.getPatternValues()) {
				if (methods.isEmpty()) {
					mappings.add("ANY " + pattern);
				}
				methods.forEach(method -> mappings.add(method.name() + " " + pattern));
			}
		}
		// Form login is handled by Spring Security's authentication filter, not an MVC
		// controller.
		mappings.add("POST /login");
		return mappings;
	}

	private static void assertRoute(PlanGuardSupport.RouteSpec route, Set<String> mappings, String securitySurface) {
		String key = route.method() + " " + route.path();
		if (!mappings.contains(key) && !mappings.contains("ANY " + route.path())) {
			throw new AssertionError(key + " has no application handler");
		}
		if (!hasSecuritySurfaceRow(route, securitySurface)) {
			throw new AssertionError(key + " has no Security Surface matrix row");
		}
	}

	private static boolean hasSecuritySurfaceRow(PlanGuardSupport.RouteSpec route, String rules) {
		String path = route.path();
		if (path.startsWith("/my/")) {
			return rules.contains("| `/my/**`");
		}
		if (path.startsWith("/staff/")) {
			return rules.contains("| `/staff/**`");
		}
		if (path.equals("/403")) {
			return route.method().equals(HttpMethod.GET.name()) && rules.contains("| `/403` (GET) |");
		}
		if (path.equals("/login")) {
			return rules.contains("| `/login` (GET, POST), `/error` |");
		}
		if (path.equals("/logout")) {
			return route.method().equals(HttpMethod.POST.name()) && rules.contains("| `/logout` (POST) |");
		}
		return rules.contains("| `" + path + "`");
	}

}
