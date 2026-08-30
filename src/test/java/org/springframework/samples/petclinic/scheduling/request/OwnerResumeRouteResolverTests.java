package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerResumeRouteResolverTests {

	@Test
	void resumeUrlIsStateDerived() {
		OfferService offers = mock(OfferService.class);
		OwnerResumeRouteResolver resolver = new OwnerResumeRouteResolver(offers);
		SchedulingRequest request = new SchedulingRequest();
		ReflectionTestUtils.setField(request, "id", 5L);
		request.setState(RequestState.READY_FOR_SUGGESTION);
		assertThat(resolver.resumeUrl(request)).isEqualTo("/owner/scheduling-requests/5/suggestion");
		request.setState(RequestState.OFFER_HELD);
		request.setActiveRequestRevisionId(3L);
		Offer offer = new Offer();
		ReflectionTestUtils.setField(offer, "id", 8L);
		when(offers.activeHeld(3L)).thenReturn(Optional.of(offer));
		assertThat(resolver.resumeUrl(request)).isEqualTo("/owner/scheduling-requests/5/offers/8");
	}

}
