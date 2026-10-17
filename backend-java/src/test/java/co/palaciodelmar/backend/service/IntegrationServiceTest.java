package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.IntegrationAvailabilityRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegrationServiceTest {

    private SuiteRepository suiteRepository;
    private SuiteNightRepository suiteNightRepository;
    private IntegrationService integrationService;

    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        suiteRepository = mock(SuiteRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        integrationService = new IntegrationService(suiteRepository, suiteNightRepository);

        apiKey = ApiKey.builder()
                .id("key_01")
                .propertyId("prop_hotel_01")
                .ownerId("host_01")
                .name("PMS Integration")
                .active(true)
                .build();
    }

    @Test
    void testUpsertNewRoomFromExternalSource() {
        when(suiteRepository.findByPropertyIdAndExternalId("prop_hotel_01", "PMS-101"))
                .thenReturn(Optional.empty());

        when(suiteRepository.save(any(Suite.class))).thenAnswer(i -> {
            Suite s = i.getArgument(0);
            s.setId("suite_new_1");
            return s;
        });

        IntegrationRoomRequest req = IntegrationRoomRequest.builder()
                .externalId("PMS-101")
                .name("Suite Marina Externa")
                .basePrice(new BigDecimal("350000"))
                .maxGuests(3)
                .type("Suite")
                .build();

        Suite saved = integrationService.upsertRoom(apiKey, req);

        assertNotNull(saved);
        assertEquals("PMS-101", saved.getExternalId());
        assertEquals("EXTERNAL", saved.getSource());
        assertEquals("prop_hotel_01", saved.getPropertyId());
        assertEquals(new BigDecimal("350000"), saved.getBasePrice());
        assertNotNull(saved.getSlug());
        assertTrue(saved.getSlug().contains("suite-marina-externa"));
    }

    @Test
    void testUpsertUpdateExistingRoom() {
        Suite existing = Suite.builder()
                .id("suite_existing_1")
                .propertyId("prop_hotel_01")
                .externalId("PMS-101")
                .source("EXTERNAL")
                .name("Old Name")
                .basePrice(new BigDecimal("300000"))
                .slug("old-slug")
                .build();

        when(suiteRepository.findByPropertyIdAndExternalId("prop_hotel_01", "PMS-101"))
                .thenReturn(Optional.of(existing));
        when(suiteRepository.save(any(Suite.class))).thenAnswer(i -> i.getArgument(0));

        IntegrationRoomRequest req = IntegrationRoomRequest.builder()
                .externalId("PMS-101")
                .name("Updated Suite Name")
                .basePrice(new BigDecimal("420000"))
                .maxGuests(4)
                .build();

        Suite updated = integrationService.upsertRoom(apiKey, req);

        assertEquals("Updated Suite Name", updated.getName());
        assertEquals(new BigDecimal("420000"), updated.getBasePrice());
        assertEquals(4, updated.getMaxGuests());
        assertEquals("old-slug", updated.getSlug()); // Mantiene slug original
    }

    @Test
    void testBlockAvailabilitySuccessAntiOverbooking() {
        Suite suite = Suite.builder()
                .id("suite_01")
                .propertyId("prop_hotel_01")
                .externalId("PMS-101")
                .branch("branch-1")
                .build();

        when(suiteRepository.findByPropertyIdAndExternalId("prop_hotel_01", "PMS-101"))
                .thenReturn(Optional.of(suite));

        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5); // 3 noches

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite_01"), eq(start), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        when(suiteNightRepository.save(any(SuiteNight.class))).thenAnswer(i -> i.getArgument(0));

        IntegrationAvailabilityRequest req = IntegrationAvailabilityRequest.builder()
                .externalId("PMS-101")
                .startDate(start)
                .endDate(end)
                .action("BLOCK")
                .reason("Venta externa en mostrador")
                .build();

        Map<String, Object> result = integrationService.manageAvailability(apiKey, req);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(3L, result.get("nightsBlocked"));

        ArgumentCaptor<SuiteNight> captor = ArgumentCaptor.forClass(SuiteNight.class);
        verify(suiteNightRepository, times(3)).save(captor.capture());

        List<SuiteNight> blockedNights = captor.getAllValues();
        assertEquals(3, blockedNights.size());
        for (SuiteNight sn : blockedNights) {
            assertEquals("suite_01", sn.getSuite());
            assertEquals("blocked", sn.getStatus());
            assertTrue(sn.getBooking().startsWith("EXT_BLOCK_"));
        }
    }

    @Test
    void testBlockAvailabilityDetectsConflictWithActiveBooking() {
        Suite suite = Suite.builder()
                .id("suite_01")
                .propertyId("prop_hotel_01")
                .externalId("PMS-101")
                .build();

        when(suiteRepository.findByPropertyIdAndExternalId("prop_hotel_01", "PMS-101"))
                .thenReturn(Optional.of(suite));

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        SuiteNight nativeBookingNight = SuiteNight.builder()
                .suite("suite_01")
                .unitSlot(1)
                .dates(List.of(start))
                .booking("book_pdm_native_123")
                .status("confirmed")
                .build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite_01"), eq(start), any(LocalDate.class)))
                .thenReturn(List.of(nativeBookingNight));

        IntegrationAvailabilityRequest req = IntegrationAvailabilityRequest.builder()
                .externalId("PMS-101")
                .startDate(start)
                .endDate(end)
                .action("BLOCK")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                integrationService.manageAvailability(apiKey, req)
        );

        assertTrue(ex.getMessage().contains("Conflicto"));
        verify(suiteNightRepository, never()).save(any());
    }

    @Test
    void testUnblockAvailabilityRemovesBlockedNights() {
        Suite suite = Suite.builder()
                .id("suite_01")
                .propertyId("prop_hotel_01")
                .externalId("PMS-101")
                .build();

        when(suiteRepository.findByPropertyIdAndExternalId("prop_hotel_01", "PMS-101"))
                .thenReturn(Optional.of(suite));

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        SuiteNight blockedNight = SuiteNight.builder()
                .suite("suite_01")
                .unitSlot(1)
                .status("blocked")
                .booking("EXT_BLOCK_abc")
                .build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite_01"), eq(start), any(LocalDate.class)))
                .thenReturn(List.of(blockedNight));

        IntegrationAvailabilityRequest req = IntegrationAvailabilityRequest.builder()
                .externalId("PMS-101")
                .startDate(start)
                .endDate(end)
                .action("UNBLOCK")
                .build();

        Map<String, Object> result = integrationService.manageAvailability(apiKey, req);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("recordsRemoved"));
        verify(suiteNightRepository).deleteAll(List.of(blockedNight));
    }
}
