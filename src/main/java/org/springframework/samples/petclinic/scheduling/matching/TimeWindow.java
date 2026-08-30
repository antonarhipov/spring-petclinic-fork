package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;

public record TimeWindow(Instant startAt, Instant endAt, boolean fallbackAllowed) {
}
