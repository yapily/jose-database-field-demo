package com.yapily.jose.database.demo.repository;

import com.yapily.jose.database.demo.model.RawPerson;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Spring Data repository to access the Raw Person Pojo from the database. Thats' our way of showing you the real data format
 * behind the scene.
 */
public interface RawPersonRepository extends ReactiveCrudRepository<RawPerson, Long> {}