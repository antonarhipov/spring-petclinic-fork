package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitCategory;
import org.springframework.samples.petclinic.owner.VisitRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingAuthorizationPolicyTests {

	private final VisitRepository visits = mock(VisitRepository.class);

	private final BookingAuthorizationPolicy policy = new BookingAuthorizationPolicy(this.visits);

	@Test
	void rejectsMissingAuthorization() {
		assertThatThrownBy(() -> this.policy.validate(null, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("AUTHORIZATION_REQUIRED");
	}

	@Test
	void acceptsCompleteOwnerAgreement() {
		BookingAuthorization authorization = new BookingAuthorization("OWNER_AGREEMENT", 2L, Instant.now(), "PHONE",
				null);
		this.policy.validate(authorization, 1);
	}

	@Test
	void rejectsIncompleteOwnerAgreement() {
		BookingAuthorization authorization = new BookingAuthorization("OWNER_AGREEMENT", null, null, null, null);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("OWNER_AGREEMENT_INCOMPLETE");
	}

	@Test
	void rejectsFollowUpWithoutSupportingVisit() {
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, null);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SUPPORTING_VISIT_REQUIRED");
	}

	@Test
	void rejectsFollowUpWhenSupportingVisitNotFound() {
		when(this.visits.findById(9)).thenReturn(Optional.empty());
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, 9);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SUPPORTING_VISIT_NOT_FOUND");
	}

	@Test
	void rejectsFollowUpWhenSupportingVisitBelongsToAnotherPet() {
		Visit visit = new Visit();
		visit.setPetId(2);
		visit.setCategory(VisitCategory.FOLLOW_UP.name());
		when(this.visits.findById(9)).thenReturn(Optional.of(visit));
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, 9);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SUPPORTING_VISIT_WRONG_PET");
	}

	@Test
	void rejectsFollowUpWhenSupportingVisitIsNotCategorizedAsFollowUpOrRecheck() {
		Visit visit = new Visit();
		visit.setPetId(1);
		visit.setCategory(VisitCategory.GENERAL.name());
		when(this.visits.findById(9)).thenReturn(Optional.of(visit));
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, 9);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SUPPORTING_VISIT_NOT_FOLLOW_UP");
	}

	@Test
	void rejectsFollowUpWhenSupportingVisitHasNoCategoryRecorded() {
		Visit visit = new Visit();
		visit.setPetId(1);
		visit.setDescription("Recheck of ear infection");
		when(this.visits.findById(9)).thenReturn(Optional.of(visit));
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, 9);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SUPPORTING_VISIT_NOT_FOLLOW_UP");
	}

	@Test
	void acceptsFollowUpWhenSupportingVisitIsDocumentedFollowUp() {
		Visit visit = new Visit();
		visit.setPetId(1);
		visit.setCategory(VisitCategory.FOLLOW_UP.name());
		when(this.visits.findById(9)).thenReturn(Optional.of(visit));
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_FOLLOW_UP", null, null, null, 9);
		this.policy.validate(authorization, 1);
	}

	@Test
	void acceptsRecheckWhenSupportingVisitIsDocumentedRecheck() {
		Visit visit = new Visit();
		visit.setPetId(1);
		visit.setCategory(VisitCategory.RECHECK.name());
		when(this.visits.findById(9)).thenReturn(Optional.of(visit));
		BookingAuthorization authorization = new BookingAuthorization("CLINIC_RECHECK", null, null, null, 9);
		this.policy.validate(authorization, 1);
	}

	@Test
	void rejectsUnknownAuthorizationBasis() {
		BookingAuthorization authorization = new BookingAuthorization("SOMETHING_ELSE", null, null, null, null);
		assertThatThrownBy(() -> this.policy.validate(authorization, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("UNKNOWN_AUTHORIZATION");
	}

	@Test
	void visitCategoryHelperIdentifiesFollowUpAndRecheckOnly() {
		Visit followUp = new Visit();
		followUp.setCategory(VisitCategory.FOLLOW_UP.name());
		Visit recheck = new Visit();
		recheck.setCategory(VisitCategory.RECHECK.name());
		Visit general = new Visit();
		general.setCategory(VisitCategory.GENERAL.name());
		Visit uncategorized = new Visit();

		assertThat(followUp.isDocumentedFollowUpOrRecheck()).isTrue();
		assertThat(recheck.isDocumentedFollowUpOrRecheck()).isTrue();
		assertThat(general.isDocumentedFollowUpOrRecheck()).isFalse();
		assertThat(uncategorized.isDocumentedFollowUpOrRecheck()).isFalse();
	}

}
