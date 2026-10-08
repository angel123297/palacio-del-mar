package co.palaciodelmar.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.data.mongodb.uri=mongodb://${MONGO_HOST:localhost}:27017/palacio_db",
	"spring.mongodb.uri=mongodb://${MONGO_HOST:localhost}:27017/palacio_db"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
