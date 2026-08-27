package org.springframework.samples.petclinic.scheduling.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LockCoordinatorTests {

	private EntityManager entityManager;

	private LockCoordinator lockCoordinator;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		lockCoordinator = new LockCoordinator(entityManager);
	}

	@Test
	void locksResourcesInExactStrictGlobalOrderAndAscendingIds() {
		Owner owner1 = new Owner();
		owner1.setId(1);
		Owner owner2 = new Owner();
		owner2.setId(2);

		Pet pet3 = new Pet();
		pet3.setId(3);
		Pet pet5 = new Pet();
		pet5.setId(5);

		Vet vet4 = new Vet();
		vet4.setId(4);

		SchedulingRequest req10 = new SchedulingRequest();
		req10.setId(10);
		SchedulingRequest req20 = new SchedulingRequest();
		req20.setId(20);

		Appointment appt15 = new Appointment();
		appt15.setId(15);

		Reservation res7 = new Reservation();
		res7.setId(7);
		Reservation res8 = new Reservation();
		res8.setId(8);

		when(entityManager.find(eq(Owner.class), eq(1), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(owner1);
		when(entityManager.find(eq(Owner.class), eq(2), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(owner2);
		when(entityManager.find(eq(Pet.class), eq(3), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(pet3);
		when(entityManager.find(eq(Pet.class), eq(5), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(pet5);
		when(entityManager.find(eq(Vet.class), eq(4), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(vet4);
		when(entityManager.find(eq(SchedulingRequest.class), eq(10), eq(LockModeType.PESSIMISTIC_WRITE)))
			.thenReturn(req10);
		when(entityManager.find(eq(SchedulingRequest.class), eq(20), eq(LockModeType.PESSIMISTIC_WRITE)))
			.thenReturn(req20);
		when(entityManager.find(eq(Appointment.class), eq(15), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(appt15);
		when(entityManager.find(eq(Reservation.class), eq(7), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(res7);
		when(entityManager.find(eq(Reservation.class), eq(8), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(res8);

		// Pass out of order
		LockCoordinator.LockedResources result = lockCoordinator.lockResources(Arrays.asList(2, 1), Arrays.asList(5, 3),
				Arrays.asList(4), Arrays.asList(20, 10), Arrays.asList(15), Arrays.asList(8, 7));

		InOrder inOrder = Mockito.inOrder(entityManager);

		// 1. Owner (1 then 2)
		inOrder.verify(entityManager).find(Owner.class, 1, LockModeType.PESSIMISTIC_WRITE);
		inOrder.verify(entityManager).find(Owner.class, 2, LockModeType.PESSIMISTIC_WRITE);

		// 2. Pet (3 then 5)
		inOrder.verify(entityManager).find(Pet.class, 3, LockModeType.PESSIMISTIC_WRITE);
		inOrder.verify(entityManager).find(Pet.class, 5, LockModeType.PESSIMISTIC_WRITE);

		// 3. Vet (4)
		inOrder.verify(entityManager).find(Vet.class, 4, LockModeType.PESSIMISTIC_WRITE);

		// 4. SchedulingRequest (10 then 20)
		inOrder.verify(entityManager).find(SchedulingRequest.class, 10, LockModeType.PESSIMISTIC_WRITE);
		inOrder.verify(entityManager).find(SchedulingRequest.class, 20, LockModeType.PESSIMISTIC_WRITE);

		// 5. Appointment (15)
		inOrder.verify(entityManager).find(Appointment.class, 15, LockModeType.PESSIMISTIC_WRITE);

		// 6. Reservation (7 then 8)
		inOrder.verify(entityManager).find(Reservation.class, 7, LockModeType.PESSIMISTIC_WRITE);
		inOrder.verify(entityManager).find(Reservation.class, 8, LockModeType.PESSIMISTIC_WRITE);

		assertThat(result.getOwners()).containsExactly(owner1, owner2);
		assertThat(result.getPets()).containsExactly(pet3, pet5);
		assertThat(result.getVets()).containsExactly(vet4);
		assertThat(result.getRequests()).containsExactly(req10, req20);
		assertThat(result.getAppointments()).containsExactly(appt15);
		assertThat(result.getReservations()).containsExactly(res7, res8);
	}

}
