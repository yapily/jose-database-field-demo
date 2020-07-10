package com.yapily.jose.database.demo.repository;

import com.yapily.jose.database.JoseDatabaseAttributeConverter;
import com.yapily.jose.database.demo.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.SettableValue;

import java.util.Optional;

/**
 * The Person writer converter is called before saving the Person POJO to the database with Spring Data.
 * It will be the one calling JOSE database and making sure it's encrypted on the fly.
 */
@WritingConverter
@Slf4j
public class PersonWriteConverter implements Converter<Person, OutboundRow>{

    private JoseDatabaseAttributeConverter attributeEncryptor;

    public PersonWriteConverter(JoseDatabaseAttributeConverter attributeEncryptor) {
        this.attributeEncryptor = attributeEncryptor;
    }

    public OutboundRow convert(Person source) {
        OutboundRow row = new OutboundRow();

        Optional.ofNullable(source.getId()).ifPresent(v -> row.put("id", SettableValue.from(v)));
        Optional.ofNullable(source.getName()).ifPresent(v -> row.put("name", SettableValue.from(v)));
        //Here we call the JOSE database attribute encryptor that will encrypt our field into a JWT
        Optional.ofNullable(source.getEmail()).ifPresent(v -> row.put("email", SettableValue.from(attributeEncryptor.convertToDatabaseColumn(v))));

        return row;
    }
}
