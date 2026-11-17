package co.palaciodelmar.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migrador temprano de índices en MongoDB.
 * Se ejecuta inmediatamente tras la instanciación de MongoTemplate (vía BeanPostProcessor),
 * garantizando que cualquier índice obsoleto o conflictivo sobre suites sea eliminado
 * ANTES de que MongoPersistentEntityIndexCreator intente crearlos en ContextRefreshedEvent.
 */
@Component
@Slf4j
public class MongoIndexMigrationPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MongoTemplate mongoTemplate) {
            migrateSuiteIndexes(mongoTemplate);
        }
        return bean;
    }

    private void migrateSuiteIndexes(MongoTemplate mongoTemplate) {
        try {
            if (!mongoTemplate.collectionExists("suites")) {
                return;
            }
            IndexOperations indexOps = mongoTemplate.indexOps("suites");
            List<IndexInfo> indexInfoList = indexOps.getIndexInfo();
            for (IndexInfo info : indexInfoList) {
                boolean hasProperty = info.getIndexFields().stream().anyMatch(f -> "propertyId".equals(f.getKey()));
                boolean hasExternal = info.getIndexFields().stream().anyMatch(f -> "externalId".equals(f.getKey()));

                if (hasProperty && hasExternal) {
                    boolean isV2WithPartial = "property_external_id_v2_idx".equals(info.getName())
                            && info.getPartialFilterExpression() != null
                            && !info.getPartialFilterExpression().isBlank();
                    if (!isV2WithPartial) {
                        log.info("Eliminando índice de suite heredado/conflictivo '{}' para migración limpia a v2...", info.getName());
                        indexOps.dropIndex(info.getName());
                        log.info("Índice '{}' eliminado con éxito.", info.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo completar la migración previa de índices de suites: {}", e.getMessage());
        }
    }
}
