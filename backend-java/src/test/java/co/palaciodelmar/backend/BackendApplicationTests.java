package co.palaciodelmar.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackendApplicationTests {

	@Test
	@org.junit.jupiter.api.DisplayName("Verificar que BackendApplication está anotado con @SpringBootApplication y @EnableScheduling")
	void contextLoads() {
		assertNotNull(BackendApplication.class);
		org.junit.jupiter.api.Assertions.assertTrue(
				BackendApplication.class.isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class),
				"BackendApplication debe estar anotado con @SpringBootApplication");
		org.junit.jupiter.api.Assertions.assertTrue(
				BackendApplication.class.isAnnotationPresent(org.springframework.scheduling.annotation.EnableScheduling.class),
				"BackendApplication debe habilitar scheduling mediante @EnableScheduling");
	}

	@Test
	@org.junit.jupiter.api.DisplayName("Verificar que PaymentWebhookService tiene constructor único anotado con @Autowired")
	void testPaymentWebhookServiceSpringConstructorResolution() {
		var constructors = co.palaciodelmar.backend.webhook.PaymentWebhookService.class.getConstructors();
		org.junit.jupiter.api.Assertions.assertEquals(1, constructors.length,
				"PaymentWebhookService debe tener un único constructor para evitar 'No default constructor found' en Spring");
		org.junit.jupiter.api.Assertions.assertTrue(
				constructors[0].isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class),
				"El constructor de PaymentWebhookService debe estar anotado con @Autowired");
	}

	@Test
	@org.junit.jupiter.api.DisplayName("Verificar que AdminService tiene constructor único anotado con @Autowired")
	void testAdminServiceSpringConstructorResolution() {
		var constructors = co.palaciodelmar.backend.service.AdminService.class.getConstructors();
		org.junit.jupiter.api.Assertions.assertEquals(1, constructors.length,
				"AdminService debe tener un único constructor para evitar ambigüedades en Spring");
		org.junit.jupiter.api.Assertions.assertTrue(
				constructors[0].isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class),
				"El constructor de AdminService debe estar anotado con @Autowired");
	}

}
