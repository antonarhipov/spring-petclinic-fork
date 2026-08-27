/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record InterpretationWindow(DayOfWeek dayOfWeek, String dayPart, LocalTime start, LocalTime end, String zoneId) {

}
