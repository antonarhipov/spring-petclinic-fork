package org.springframework.samples.petclinic.vet;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {

	Optional<Specialty> findByName(String name);

	List<Specialty> findByActiveTrue();

	List<Specialty> findByActiveTrueOrderByName();

}
