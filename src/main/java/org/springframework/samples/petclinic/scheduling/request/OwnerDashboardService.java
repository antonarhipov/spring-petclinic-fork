package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerDashboardService {

	private final OwnerRepository owners;

	private final SchedulingRequestRepository requests;

	private final OwnerResumeRouteResolver resumeRoutes;

	public OwnerDashboardService(OwnerRepository owners, SchedulingRequestRepository requests,
			OwnerResumeRouteResolver resumeRoutes) {
		this.owners = owners;
		this.requests = requests;
		this.resumeRoutes = resumeRoutes;
	}

	@Transactional(readOnly = true)
	public OwnerDashboardView load(Integer ownerId) {
		Owner owner = this.owners.findById(ownerId).orElseThrow(OwnerResourceNotFoundException::new);
		List<SchedulingRequest> open = this.requests.findByOwnerIdOrderByUpdatedAtDesc(ownerId)
			.stream()
			.filter(request -> request.getState() != RequestState.CLOSED
					&& request.getState() != RequestState.CONFIRMED)
			.toList();
		SchedulingRequest active = open.isEmpty() ? null : open.get(0);
		return new OwnerDashboardView(owner, active, this.resumeRoutes.resumeUrl(active));
	}

	public String resumeUrl(SchedulingRequest request) {
		return this.resumeRoutes.resumeUrl(request);
	}

	public record OwnerDashboardView(Owner owner, SchedulingRequest activeRequest, String resumeUrl) {
	}

}
