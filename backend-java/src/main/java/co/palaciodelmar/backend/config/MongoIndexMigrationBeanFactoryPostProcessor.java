package co.palaciodelmar.backend.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Migrador de índices pre-arranque en MongoDB.
 * Implementa BeanFactoryPostProcessor y PriorityOrdered (HIGHEST_PRECEDENCE) para ejecutarse
 * ANTES de que Spring Data cree ningún bean singleton (MongoTemplate, MongoMappingContext,
 * o MongoPersistentEntityIndexCreator).
 *
 * Abre una conexión directa y efímera vía MongoClient para eliminar cualquier índice
 * obsoleto o conflictivo sobre suites antes de que Spring Data intente crearlos.
 */
@Slf4j
@Component
public class MongoIndexMigrationBeanFactoryPostProcessor implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null) {
            return;
        }
        String mongoUri = environment.getProperty("spring.data.mongodb.uri");
        if (mongoUri == null || mongoUri.isBlank()) {
            return;
        }

        try (MongoClient client = MongoClients.create(mongoUri)) {
            com.mongodb.ConnectionString connStr = new com.mongodb.ConnectionString(mongoUri);
            String dbName = connStr.getDatabase() != null ? connStr.getDatabase() : "palacio_db";
            MongoDatabase db = client.getDatabase(dbName);

            boolean collectionExists = false;
            for (String colName : db.listCollectionNames()) {
                if ("suites".equals(colName)) {
                    collectionExists = true;
                    break;
                }
            }
            if (!collectionExists) {
                return;
            }

            MongoCollection<Document> suites = db.getCollection("suites");
            for (Document idx : suites.listIndexes()) {
                String name = idx.getString("name");
                Document key = idx.get("key", Document.class);
                if (key != null && key.containsKey("propertyId") && key.containsKey("externalId")) {
                    if (!"property_external_id_v2_idx".equals(name)) {
                        log.info("BeanFactoryPostProcessor: Eliminando índice legacy conflictivo '{}' de suites antes de inicializar Spring Data...", name);
                        suites.dropIndex(name);
                        log.info("BeanFactoryPostProcessor: Índice '{}' eliminado exitosamente.", name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("BeanFactoryPostProcessor: No se pudo verificar/eliminar índices legacy de suites: {}", e.getMessage());
        }
    }
}
