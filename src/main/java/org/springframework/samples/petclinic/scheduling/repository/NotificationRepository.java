package org.springframework.samples.petclinic.scheduling.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.Notification;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

	List<Notification> findByOwnerIdOrderByCreatedAtDesc(Integer ownerId);

	long countByOwnerIdAndReadFalse(Integer ownerId);

	Optional<Notification> findByIdAndOwnerId(Integer id, Integer ownerId);

}
