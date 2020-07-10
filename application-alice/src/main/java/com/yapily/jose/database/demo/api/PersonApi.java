package com.yapily.jose.database.demo.api;

import com.yapily.jose.database.demo.model.Person;
import com.yapily.jose.database.demo.model.RawPerson;
import com.yapily.jose.database.demo.repository.PersonRepository;
import com.yapily.jose.database.demo.repository.RawPersonRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/persons")
@Slf4j
public class PersonApi {

    //This repository auto-decrypt the email, thanks to the converters
    private final PersonRepository personRepository;
    //This repository won't convert the email automatically and will return the raw data.
    //This repository is an helper, to help you visualise how works the encryption
    private final RawPersonRepository rawPersonRepository;

    public PersonApi(PersonRepository personRepository, RawPersonRepository rawPersonRepository) {
        this.personRepository = personRepository;
        this.rawPersonRepository = rawPersonRepository;
    }

    /**
     * Return the persons from the database
     * @return
     */
    @RequestMapping(
            value = "/",
            method = RequestMethod.GET
    )
    public Flux<Person> getPersons() {
        return personRepository.findAll();
    }

    /**
     * Return the raw persons from the database, meaning it would return the encrypted version of the person.
     * This is an utility api to help you visualise the real format of the data stored in the DB
     * @return
     */
    @RequestMapping(
            value = "/raw",
            method = RequestMethod.GET
    )
    public Flux<RawPerson> getRawPersons() {
        return rawPersonRepository.findAll();
    }

    /**
     * Generate a bunch of persons. This is our way to show you the status of the JOSE field overtime
     * @param nbEntries the number of entries you want to create
     * @return the persons created
     */
    @RequestMapping(
            value = "/random",
            method = RequestMethod.POST
    )
    public Flux<Person> createRandomPerson(
            @RequestParam(name = "nb-entries", defaultValue = "1") int nbEntries
    ) {
        List<Person> persons = new ArrayList<>();
        for(int i = 0; i < nbEntries; i++) {
            String randomName = RandomStringUtils.randomAlphabetic(10);
            persons.add(Person.builder()
                    .name(randomName)
                    .email(randomName + "@yapily.com")
                    .build());
        }

        return personRepository.saveAll(persons);
    }
}
