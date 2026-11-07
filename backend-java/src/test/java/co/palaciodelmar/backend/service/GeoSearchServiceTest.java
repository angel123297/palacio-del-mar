package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.GeoPlaceDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GeoSearchServiceTest {

    @Test
    @DisplayName("Pilar 4: Calcula distancias exactas en Cartagena, tiempos a pie y filtra por categoría")
    void testFindNearbyLandmarksAndSuiteOrigin() {
        BranchRepository branchRepo = mock(BranchRepository.class);
        PropertyRepository propRepo = mock(PropertyRepository.class);
        SuiteRepository suiteRepo = mock(SuiteRepository.class);

        Branch centroBranch = Branch.builder()
                .id("br-centro")
                .slug("centro-historico")
                .name("Palacio del Mar · Centro Histórico")
                .zone("Centro Histórico")
                .address("Calle de la Factoría, Cartagena")
                .active(true)
                .location(Branch.GeoLocation.builder().lat(10.4236).lng(-75.5515).build())
                .build();
        when(branchRepo.findAll()).thenReturn(List.of(centroBranch));
        when(branchRepo.findById("br-centro")).thenReturn(Optional.of(centroBranch));

        Suite colonialSuite = Suite.builder()
                .id("s-colonial")
                .slug("suite-colonial-centro")
                .branch("br-centro")
                .build();
        when(suiteRepo.findById("suite-colonial-centro")).thenReturn(Optional.empty());
        when(suiteRepo.findBySlug("suite-colonial-centro")).thenReturn(Optional.of(colonialSuite));

        GeoSearchService geoService = new GeoSearchService(null, branchRepo, propRepo, suiteRepo, new ObjectMapper());

        List<GeoPlaceDTO> nearby = geoService.findNearbyForSuite("suite-colonial-centro", 1.5, null, 5);
        assertFalse(nearby.isEmpty());
        // Plaza Santo Domingo está a ~60 metros de (10.4236, -75.5515)
        assertEquals("Plaza Santo Domingo", nearby.get(0).getName());
        assertTrue(nearby.get(0).getDistanceKm() < 0.25);
        assertNotNull(nearby.get(0).getWalkMinutes());

        // Filtro por categoría "yacht" (muelles de embarque)
        List<GeoPlaceDTO> marinas = geoService.findNearby(10.4225, -75.5492, 5.0, "yacht", 5);
        assertFalse(marinas.isEmpty());
        assertTrue(marinas.stream().allMatch(p -> "yacht".equalsIgnoreCase(p.getCategory())));

        // Distancia entre Torre del Reloj y Playa Bocagrande (~2.2 km)
        Double distKm = geoService.distanceBetweenKm("poi:torre-del-reloj", "poi:playa-bocagrande");
        assertNotNull(distKm);
        assertTrue(distKm > 1.5 && distKm < 3.0);

        // Coordenadas inválidas lanzan IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> geoService.findNearby(95.0, -75.55, 5.0, null, 5));
    }

    @Test
    @DisplayName("Pilar 4: Sincroniza con Redis Geo y depura propiedades obsoletas/suspendidas del índice")
    @SuppressWarnings("unchecked")
    void testRedisGeoSyncPrunesSuspendedProperties() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        GeoOperations<String, String> geoOps = mock(GeoOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.keys(GeoSearchService.META_HASH_KEY)).thenReturn(Set.of("property:suspendida-hotel", "poi:plaza-santo-domingo"));

        PropertyRepository propRepo = mock(PropertyRepository.class);
        Property suspended = Property.builder()
                .id("p-1")
                .slug("suspendida-hotel")
                .name("Hotel Suspendido")
                .status("SUSPENDED")
                .active(true)
                .build();
        when(propRepo.findAll()).thenReturn(List.of(suspended));

        GeoSearchService geoService = new GeoSearchService(redisTemplate, null, propRepo, null, new ObjectMapper());
        geoService.refreshCatalogIndex();

        verify(geoOps).add(eq(GeoSearchService.GEO_KEY), anyMap());
        verify(geoOps).remove(GeoSearchService.GEO_KEY, "property:suspendida-hotel");
        verify(hashOps).delete(GeoSearchService.META_HASH_KEY, "property:suspendida-hotel");
    }
}
