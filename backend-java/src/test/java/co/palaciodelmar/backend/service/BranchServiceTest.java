package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BranchResponseDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BranchServiceTest {

    private BranchRepository branchRepository;
    private SuiteRepository suiteRepository;
    private PropertyRepository propertyRepository;
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        branchService = new BranchService(branchRepository, suiteRepository, propertyRepository);
    }

    @Test
    @DisplayName("El conteo de suites por sede solo empareja por ID: un slug en el campo branch es dato corrupto y no cuenta")
    void suiteCountMatchesOnlyByBranchIdNotBySlugText() {
        Branch centro = Branch.builder()
                .id("br-centro")
                .name("Palacio del Mar · Centro Histórico")
                .slug("centro-historico")
                .zone("Centro Histórico")
                .active(true)
                .build();

        Suite suiteConBranchId = Suite.builder()
                .id("s-1")
                .name("Suite Centro")
                .branch("br-centro")
                .basePrice(new BigDecimal("100000"))
                .active(true)
                .build();

        Suite suiteCorruptaConSlug = Suite.builder()
                .id("s-2")
                .name("Suite con slug corrupto")
                .branch("centro-historico")
                .basePrice(new BigDecimal("200000"))
                .active(true)
                .build();

        when(branchRepository.findByActiveTrueOrderByOrderAsc()).thenReturn(List.of(centro));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(suiteConBranchId, suiteCorruptaConSlug));
        when(propertyRepository.findByStatus("APPROVED")).thenReturn(List.of());

        List<BranchResponseDTO> branches = branchService.getAllActiveBranches();

        assertEquals(1, branches.size());
        assertEquals(1, branches.get(0).getRoomTypes(),
                "Solo la suite con branch == ID de la sede debe contar; el texto del slug nunca es un identificador");
    }
}
