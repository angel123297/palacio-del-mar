package co.palaciodelmar.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.data.mongodb.uri=mongodb://${MONGO_HOST:localhost}:27017/palacio_db",
	"spring.mongodb.uri=mongodb://${MONGO_HOST:localhost}:27017/palacio_db",
	"jwt.secret=test-only-secret-not-used-in-production-0123456789abcdef"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
