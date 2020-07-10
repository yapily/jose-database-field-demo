package com.yapily.jose.database.demo.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The POJO person, that we will use for this demo to show the encryption
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("person")
public class Person {
    @Id
    private Long id;

    private String name;
    //The email will be configured to be encrypted. Although in R2DBC, it's happening in the convertor
    private String email;
}
