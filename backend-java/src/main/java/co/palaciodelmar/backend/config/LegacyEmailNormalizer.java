package co.palaciodelmar.backend.config;

import co.palaciodelmar.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migración idempotente: el registro y el login normalizan emails a minúsculas, pero cuentas
 * insertadas directamente en Mongo con mayúsculas quedan como "cuentas fantasma" imposibles de
 * loguear. Al arrancar se pasan a minúsculas, salvo que ya exista otra cuenta con ese email
 * en minúsculas (en ese caso se omite y se deja registrado para revisión manual).
 */
@Component
@Order(0)
@Slf4j
public class LegacyEmailNormalizer implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    public LegacyEmailNormalizer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            normalize();
        } catch (Exception e) {
            log.warn("No se pudo normalizar emails legacy: {}", e.getMessage());
        }
    }

    public int normalize() {
        List<User> mixedCase = mongoTemplate.find(
                new Query(Criteria.where("email").regex("[A-Z]")), User.class);
        int fixed = 0;
        for (User u : mixedCase) {
            if (u.getEmail() == null) continue;
            String lower = u.getEmail().trim().toLowerCase();
            boolean clash = mongoTemplate.exists(
                    new Query(Criteria.where("email").is(lower).and("_id").ne(u.getId())), User.class);
            if (clash) {
                log.warn("Email legacy {} no se normaliza: ya existe una cuenta con {}", u.getEmail(), lower);
                continue;
            }
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(u.getId())),
                    new Update().set("email", lower), User.class);
            fixed++;
        }
        if (fixed > 0) {
            log.info("Emails legacy normalizados a minúsculas: {}", fixed);
        }
        return fixed;
    }
}
