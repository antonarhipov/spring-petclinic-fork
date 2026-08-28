package org.springframework.samples.petclinic.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NavigationSecurityTests {

	@Test
	void ownerTemplateDoesNotContainCalendarRoute() throws Exception {
		String ownerTemplate = Files.readString(Path.of("src/main/resources/templates/scheduling/offer.html"));
		assertThat(ownerTemplate).doesNotContain("/staff/calendar");
	}

}
