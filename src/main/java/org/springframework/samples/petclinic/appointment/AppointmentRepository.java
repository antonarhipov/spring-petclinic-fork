package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	@Query("SELECT a FROM Appointment a WHERE a.vetId = :vetId AND a.bookingState = :bookingState AND a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findOverlappingByVet(@Param("vetId") Integer vetId,
			@Param("bookingState") BookingState bookingState, @Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt);

	@Query("SELECT a FROM Appointment a WHERE a.petId = :petId AND a.bookingState = :bookingState AND a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findOverlappingByPet(@Param("petId") Integer petId,
			@Param("bookingState") BookingState bookingState, @Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt);

	@Query("SELECT a FROM Appointment a WHERE a.ownerId = :ownerId AND a.bookingState = :bookingState AND a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findOverlappingByOwner(@Param("ownerId") Integer ownerId,
			@Param("bookingState") BookingState bookingState, @Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt);

	@Query("SELECT a FROM Appointment a WHERE a.bookingState = :bookingState AND a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findOverlappingAll(@Param("bookingState") BookingState bookingState,
			@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

	@Query("SELECT a FROM Appointment a WHERE a.vetId = :vetId AND a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findByVetAndInterval(@Param("vetId") Integer vetId, @Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt);

	@Query("SELECT a FROM Appointment a WHERE a.startAt < :endAt AND a.endAt > :startAt")
	List<Appointment> findByInterval(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

	List<Appointment> findByOwnerId(Integer ownerId);

	List<Appointment> findByOwnerIdOrderByStartAtDesc(Integer ownerId);

	List<Appointment> findByPetId(Integer petId);

	java.util.Optional<Appointment> findByIdAndOwnerId(Long id, Integer ownerId);

	boolean existsByOwnerId(Integer ownerId);

	boolean existsByPetId(Integer petId);

	List<Appointment> findByBookingStateAndEndAtAfter(BookingState bookingState, Instant instant);

}
