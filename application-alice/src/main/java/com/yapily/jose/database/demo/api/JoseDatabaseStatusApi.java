package com.yapily.jose.database.demo.api;

import com.nimbusds.jose.Header;
import com.nimbusds.jose.JOSEObject;
import com.yapily.jose.database.JoseDatabaseConfig;
import com.yapily.jose.database.JoseDatabaseKeyStatus;
import com.yapily.jose.database.demo.repository.RawPersonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(value = "/database")
@Slf4j
public class JoseDatabaseStatusApi {

    @Autowired
    private RawPersonRepository rawPersonRepository;
    @Autowired
    private JoseDatabaseConfig joseDatabaseConfig;

    /**
     * Utility endpoints to show you the state of the database fields. It describes which fields is using which keys and
     * provides a summary
     * @param isDetailsEnabled
     * @return
     */
    @RequestMapping(
            value = "/status",
            method = RequestMethod.GET
    )
    public Mono<String> getStatus(
            @RequestParam(name = "details", defaultValue = "false") boolean isDetailsEnabled
    ) {
        return rawPersonRepository.findAll().collectList().flatMap(rawPersons -> {
            StringBuilder status = new StringBuilder();
            status.append("Status of the progress database at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())).append("\n");
            status.append("- Nb of rows: " + rawPersons.size()).append("\n");

            //Prepare some counters
            Map<String, Long> countersJWTsByKeyID = new HashMap<>();
            Map<JoseDatabaseKeyStatus, Long> countersJwtsByKeyType = new HashMap<>();
            countersJwtsByKeyType.put(JoseDatabaseKeyStatus.VALID, 0l);
            countersJwtsByKeyType.put(JoseDatabaseKeyStatus.EXPIRED, 0l);
            countersJwtsByKeyType.put(JoseDatabaseKeyStatus.REVOKED, 0l);

            //For providing deep dive details for each row
            StringBuilder rowsDetails = new StringBuilder();

            // Introspecting each raw and extracting some information
            rawPersons.stream().forEach(person -> {
                rowsDetails
                        .append("- id: '").append(person.getId()).append("'\n")
                        .append("  Name: '").append(person.getName()).append("'\n")
                        .append("  Raw email: '").append(person.getEmail()).append("'\n");
                //Introspect the JWT
                try {
                    JOSEObject joseObject = JOSEObject.parse(person.getEmail());
                    incrementCounter(countersJWTsByKeyID, joseObject.getHeader());
                    rowsDetails.append("  JWT header: ").append(joseObject.getHeader()).append("'\n");

                    //Increment counter for the nb Jwts by key type
                    getKeyType(joseObject.getHeader()).ifPresent(t -> countersJwtsByKeyType.put(t, countersJwtsByKeyType.get(t) + 1));

                    //Look payload type
                    JOSEObject insideJoseObject = JOSEObject.parse(joseObject.getPayload().toString());
                    incrementCounter(countersJWTsByKeyID, insideJoseObject.getHeader());
                    rowsDetails.append("  Inside JWT header: ").append(insideJoseObject.getHeader()).append("'\n");
                } catch (ParseException e) {
                    rowsDetails.append("  Couldn't parse raw email JWT'");
                    log.error("Couldn't read JWT email from {}", person.getId(), e);
                }
                rowsDetails.append("\n");
            });
            status.append("-------------\n");
            status.append("Number of entries by key type:\n");
            countersJwtsByKeyType.entrySet().stream().forEach(e -> {
                status.append("- ").append(e.getKey()).append(" : ").append(e.getValue()).append(" ;\n");
            });
            if (!countersJWTsByKeyID.isEmpty()) {
                status.append("-------------\n");
                status.append("Number of JWTs using keys:\n");
                countersJWTsByKeyID.entrySet().stream().forEach(e -> {
                    status.append("- ").append(e.getKey()).append(" : ").append(e.getValue()).append(" ;\n");
                });
            }
            if (isDetailsEnabled) {
                status.append("-------------\n");
                status.append("Details of each row: \n");
                status.append(rowsDetails);
            }
            return Mono.just(status.toString());
        });
    }

    private Optional<JoseDatabaseKeyStatus> getKeyType(Header header) {
        String kid = header.toJSONObject().getAsString("kid");
        if (joseDatabaseConfig.getValidJwkSet().getKeyByKeyId(kid) != null) {
            return Optional.of(JoseDatabaseKeyStatus.VALID);
        }
        if (joseDatabaseConfig.getExpiredJwkSet().getKeyByKeyId(kid) != null) {
            return Optional.of(JoseDatabaseKeyStatus.EXPIRED);
        }
        if (joseDatabaseConfig.getRevokedJwkSet().getKeyByKeyId(kid) != null) {
            return Optional.of(JoseDatabaseKeyStatus.REVOKED);
        }
        return Optional.empty();
    }

    private void incrementCounter(Map<String, Long> counters, Header header) {
        String kid = header.toJSONObject().getAsString("kid");
        if (!counters.containsKey(kid)) {
            counters.put(kid, 0l);
        }
        counters.put(kid, counters.get(kid) + 1);
    }
}
