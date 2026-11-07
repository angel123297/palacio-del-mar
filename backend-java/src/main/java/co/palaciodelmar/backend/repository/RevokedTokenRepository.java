package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.RevokedToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends MongoRepository<RevokedToken, String> {
}
