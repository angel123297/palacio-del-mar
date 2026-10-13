package co.palaciodelmar.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

    private static org.springframework.context.ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(BackendApplication.class, args);
    }

    public static org.springframework.context.ApplicationContext getContext() {
        return context;
    }

}
