package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query("""
			select appointment from Appointment appointment
			where appointment.vet.id = :vetId
			and appointment.date = :date
			and appointment.status in :blockingStatuses
			and appointment.startTime < :endTime
			and appointment.endTime > :startTime
			""")
	List<Appointment> findOverlapping(@Param("vetId") int vetId, @Param("date") LocalDate date,
			@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
			@Param("blockingStatuses") List<AppointmentStatus> blockingStatuses);

	@Query("""
			select appointment from Appointment appointment
			where appointment.id <> :appointmentId
			and appointment.vet.id = :vetId
			and appointment.date = :date
			and appointment.status in :blockingStatuses
			and appointment.startTime < :endTime
			and appointment.endTime > :startTime
			""")
	List<Appointment> findOverlappingOther(@Param("appointmentId") int appointmentId, @Param("vetId") int vetId,
			@Param("date") LocalDate date, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
			@Param("blockingStatuses") List<AppointmentStatus> blockingStatuses);

	long countByStatusIn(List<AppointmentStatus> statuses);

	List<Appointment> findByDateAndStatusInOrderByVetIdAscStartTimeAsc(LocalDate date,
			List<AppointmentStatus> statuses);

	@Query("""
			select appointment from Appointment appointment
			join fetch appointment.pet
			join fetch appointment.vet
			left join fetch appointment.request
			where appointment.status in :statuses
			and (appointment.date > :date or (appointment.date = :date and appointment.endTime > :time))
			order by appointment.date, appointment.startTime, appointment.id
			""")
	List<Appointment> findProtectedFrom(@Param("date") LocalDate date, @Param("time") LocalTime time,
			@Param("statuses") List<AppointmentStatus> statuses);

	@Query("select appointment from Appointment appointment, Owner owner join owner.pets pet where owner.id = :ownerId and appointment.pet = pet order by appointment.date, appointment.startTime")
	List<Appointment> findByPetOwnerIdOrderByDateAscStartTimeAsc(@Param("ownerId") Integer ownerId);

	@Query("select appointment from Appointment appointment, Owner owner join owner.pets pet where appointment.id = :id and owner.id = :ownerId and appointment.pet = pet")
	java.util.Optional<Appointment> findByIdAndPetOwnerId(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

}
