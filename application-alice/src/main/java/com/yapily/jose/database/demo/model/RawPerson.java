package com.yapily.jose.database.demo.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The Raw person is a POJO we will use to show you the actual object stored in the database.
 * For that Pojo, we won't setup a JOSE database field convertor. Therefore, the email will be encrypted as a JWT
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("person")  // <- connected to the same database than the Person Pojo for showing you the actual database content
public class RawPerson {
    @Id
    private Long id;

    private String name;
    private String email;
}
