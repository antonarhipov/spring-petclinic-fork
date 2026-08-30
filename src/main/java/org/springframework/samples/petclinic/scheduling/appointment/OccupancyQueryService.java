package org.springframework.samples.petclinic.scheduling.appointment;

import java.util.List;

import org.springframework.samples.petclinic.scheduling.matching.OccupancyFact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccupancyQueryService {

	private final ReservationBlockRepository blocks;

	public OccupancyQueryService(ReservationBlockRepository blocks) {
		this.blocks = blocks;
	}

	@Transactional(readOnly = true)
	public List<OccupancyFact> activeBlocks() {
		return this.blocks.findAll()
			.stream()
			.map(block -> new OccupancyFact(block.getResourceType(), block.getResourceId(), block.getBlockStart()))
			.toList();
	}

}
