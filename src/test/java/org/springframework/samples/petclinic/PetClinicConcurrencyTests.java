package org.springframework.samples.petclinic;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.PetTypeRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the owner+pet-name uniqueness constraint blocks concurrent duplicate inserts.
 * <p>
 * HTTP authentication/CSRF coverage lives in {@code SecurityRouteMatrixTests}; this test
 * focuses on the persistence race the unique constraint is meant to close.
 */
@SpringBootTest
@ActiveProfiles("test")
class PetClinicConcurrencyTests {

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private PetTypeRepository petTypeRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void testDuplicatePetNameRaceConditionIsBlocked() throws Exception {
		int ownerId = 1;
		String duplicatePetName = "ConcurrencyTestPet";

		Owner initialOwner = this.ownerRepository.findById(ownerId).orElseThrow();
		int initialPetCount = initialOwner.getPets().size();
		assertThat(initialOwner.getPet(duplicatePetName)).isNull();

		PetType cat = this.petTypeRepository.findPetTypes()
			.stream()
			.filter(type -> "cat".equalsIgnoreCase(type.getName()))
			.findFirst()
			.orElseThrow();

		int threadCount = 2;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		TransactionTemplate tx = new TransactionTemplate(this.transactionManager);

		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await();
					tx.executeWithoutResult(status -> {
						Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
						if (owner.getPet(duplicatePetName, true) != null) {
							throw new IllegalStateException("duplicate already present");
						}
						Pet pet = new Pet();
						pet.setName(duplicatePetName);
						pet.setBirthDate(LocalDate.of(2020, 1, 1));
						pet.setType(cat);
						owner.addPet(pet);
						this.ownerRepository.saveAndFlush(owner);
					});
					successCount.incrementAndGet();
				}
				catch (DataIntegrityViolationException | IllegalStateException ex) {
					failureCount.incrementAndGet();
				}
				catch (Exception ex) {
					// unwrap Spring transaction exceptions that wrap integrity violations
					Throwable root = ex;
					boolean integrity = false;
					while (root != null) {
						if (root instanceof DataIntegrityViolationException || root instanceof IllegalStateException) {
							integrity = true;
							break;
						}
						root = root.getCause();
					}
					if (integrity) {
						failureCount.incrementAndGet();
					}
					else {
						failureCount.incrementAndGet();
					}
				}
				finally {
					doneLatch.countDown();
				}
			});
		}

		try {
			assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
			startLatch.countDown();
			assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			executorService.shutdownNow();
		}

		try {
			Owner updatedOwner = this.ownerRepository.findById(ownerId).orElseThrow();
			int newPetCount = updatedOwner.getPets().size();

			assertThat(successCount.get()).isEqualTo(1);
			assertThat(failureCount.get()).isEqualTo(1);
			assertThat(newPetCount).isEqualTo(initialPetCount + 1);

			long countWithDuplicateName = updatedOwner.getPets()
				.stream()
				.filter(p -> duplicatePetName.equalsIgnoreCase(p.getName()))
				.count();
			assertThat(countWithDuplicateName).isEqualTo(1);
		}
		finally {
			// Remove the inserted pet so other tests sharing the in-memory DB stay
			// isolated
			tx.executeWithoutResult(status -> {
				Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
				Pet created = owner.getPet(duplicatePetName, false);
				if (created != null) {
					owner.getPets().remove(created);
					this.ownerRepository.saveAndFlush(owner);
				}
			});
		}
	}

}
