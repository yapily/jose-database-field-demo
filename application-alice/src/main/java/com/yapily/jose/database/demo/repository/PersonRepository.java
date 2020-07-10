package com.yapily.jose.database.demo.repository;

import com.yapily.jose.database.demo.model.Person;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Spring Data repository to access the Person Pojo from the database
 */
public interface PersonRepository extends ReactiveCrudRepository<Person, Long> {}