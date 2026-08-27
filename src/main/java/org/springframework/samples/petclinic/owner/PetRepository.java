package org.springframework.samples.petclinic.owner;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PetRepository extends JpaRepository<Pet, Integer> {

	@Query("select p from Owner o join o.pets p where o.id = :ownerId")
	List<Pet> findByOwnerId(@Param("ownerId") Integer ownerId);

	@Query("select p from Owner o join o.pets p where o.id = :ownerId and p.id = :id")
	Optional<Pet> findByIdAndOwnerId(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

}
