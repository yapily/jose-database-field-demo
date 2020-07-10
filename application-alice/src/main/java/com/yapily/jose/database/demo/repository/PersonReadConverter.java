package com.yapily.jose.database.demo.repository;

import com.yapily.jose.database.JoseDatabaseAttributeConverter;
import com.yapily.jose.database.demo.model.Person;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * The Person convertor will automatically decrypt the email field on the fly. Converting the JWT into a String
 */
@ReadingConverter
@Slf4j
public class PersonReadConverter implements Converter<Row, Person>{

    private JoseDatabaseAttributeConverter attributeEncryptor;

    public PersonReadConverter(JoseDatabaseAttributeConverter attributeEncryptor) {
        this.attributeEncryptor = attributeEncryptor;
    }

    public Person convert(Row source) {

        return new Person(
                source.get("id", Long.class),
                source.get("name", String.class),
                //Here we call the JOSE database attribute encryptor that will decrypt our field
                attributeEncryptor.convertToEntityAttribute(source.get("email", String.class))
        );
    }
}
