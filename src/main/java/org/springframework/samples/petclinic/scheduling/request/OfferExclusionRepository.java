package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferExclusionRepository extends JpaRepository<OfferExclusion, Long> {

	List<OfferExclusion> findByWorkflowRevisionId(Long workflowRevisionId);

	boolean existsByOfferId(Long offerId);

}
