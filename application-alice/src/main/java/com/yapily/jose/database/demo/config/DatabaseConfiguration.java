package com.yapily.jose.database.demo.config;

import com.yapily.jose.database.JoseDatabaseAttributeConverter;
import com.yapily.jose.database.demo.repository.PersonReadConverter;
import com.yapily.jose.database.demo.repository.PersonWriteConverter;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableR2dbcRepositories
public class DatabaseConfiguration extends AbstractR2dbcConfiguration {

    @Value("${spring.r2dbc.host}")
    private String host;
    @Value("${spring.r2dbc.port}")
    private Integer port;
    @Value("${spring.r2dbc.database}")
    private String database;
    @Value("${spring.r2dbc.username}")
    private String username;
    @Value("${spring.r2dbc.password}")
    private String password;

    /**
     * The attribute encryptor is an item from the JOSE-Database.
     */
    private final JoseDatabaseAttributeConverter attributeEncryptor;

    public DatabaseConfiguration(JoseDatabaseAttributeConverter attributeEncryptor) {
        this.attributeEncryptor = attributeEncryptor;
    }

    /**
     * The connection factory, to connect to our PostGreSQL database.
     * @return the connection factory
     */
    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                                                 .host(host)
                                                 .port(port)
                                                 .database(database)
                                                 .username(username)
                                                 .password(password)
                                                 .build()
        );
    }

    /**
     * The R2DBC configuration to automatically convert a POJO that has an encrypted field.
     * @return R2DBC convertors
     */
    @Bean
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {

        List<Converter<?, ?>> converterList = new ArrayList<Converter<?, ?>>();
        converterList.add(new PersonReadConverter(attributeEncryptor));
        converterList.add(new PersonWriteConverter(attributeEncryptor));
        return new R2dbcCustomConversions(getStoreConversions(), converterList);
    }
}