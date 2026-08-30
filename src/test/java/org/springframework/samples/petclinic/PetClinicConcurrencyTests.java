package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.support.StaffHttpSupport;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "petclinic.account.bootstrap-enabled=true")
public class PetClinicConcurrencyTests {

	@LocalServerPort
	private int port;

	@Autowired
	private OwnerRepository ownerRepository;

	@Test
	public void testDuplicatePetNameRaceConditionIsBlocked() throws Exception {
		int ownerId = 1;
		Optional<Owner> initialOwnerOpt = this.ownerRepository.findById(ownerId);
		assertThat(initialOwnerOpt).isPresent();
		Owner owner = initialOwnerOpt.get();

		int initialPetCount = owner.getPets().size();
		String duplicatePetName = "ConcurrencyTestPet";

		assertThat(owner.getPet(duplicatePetName)).isNull();

		int threadCount = 2;
		StaffHttpSupport.StaffSession[] sessions = new StaffHttpSupport.StaffSession[threadCount];
		for (int i = 0; i < threadCount; i++) {
			sessions[i] = StaffHttpSupport.loginAsAdminWithCsrf(this.port);
		}
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		for (int i = 0; i < threadCount; i++) {
			StaffHttpSupport.StaffSession session = sessions[i];
			executorService.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await();
					String body = "name=" + URLEncoder.encode(duplicatePetName, StandardCharsets.UTF_8)
							+ "&birthDate=2020-01-01&type=cat&_csrf="
							+ URLEncoder.encode(session.csrf(), StandardCharsets.UTF_8);
					HttpResponse<String> response = session.client()
						.send(HttpRequest
							.newBuilder(
									URI.create("http://localhost:" + this.port + "/owners/" + ownerId + "/pets/new"))
							.header("Content-Type", "application/x-www-form-urlencoded")
							.POST(HttpRequest.BodyPublishers.ofString(body))
							.build(), HttpResponse.BodyHandlers.ofString());
					String responseBody = response.body();
					if (response.statusCode() >= 200 && response.statusCode() < 400
							&& (responseBody == null || !responseBody.contains("is already in use"))) {
						successCount.incrementAndGet();
					}
					else {
						failureCount.incrementAndGet();
					}
				}
				catch (Exception e) {
					failureCount.incrementAndGet();
				}
				finally {
					doneLatch.countDown();
				}
			});
		}

		try {
			boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
			assertThat(ready).isTrue();
			startLatch.countDown();
			boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
			assertThat(completed).isTrue();
		}
		finally {
			executorService.shutdown();
		}

		Owner updatedOwner = this.ownerRepository.findById(ownerId).get();
		int newPetCount = updatedOwner.getPets().size();

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(newPetCount).isEqualTo(initialPetCount + 1);

		long countWithDuplicateName = updatedOwner.getPets()
			.stream()
			.filter(p -> duplicatePetName.equalsIgnoreCase(p.getName()))
			.count();
		assertThat(countWithDuplicateName).isEqualTo(1);
	}

}
