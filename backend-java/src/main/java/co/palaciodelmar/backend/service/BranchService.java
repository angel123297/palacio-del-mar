package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BranchResponseDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final SuiteRepository suiteRepository;
    private final PropertyRepository propertyRepository;

    public List<BranchResponseDTO> getAllActiveBranches() {
        List<Branch> branches = branchRepository.findByActiveTrueOrderByOrderAsc();
        List<Suite> activeSuites = suiteRepository.findByActiveTrue();

        List<BranchResponseDTO> dtoList = new ArrayList<>(branches.stream().map(b -> {
            List<Suite> branchSuites = activeSuites.stream()
                    .filter(s -> b.getId().equals(s.getBranch()) || (b.getSlug() != null && b.getSlug().equalsIgnoreCase(s.getBranch())))
                    .toList();
            BigDecimal minPrice = branchSuites.stream()
                    .map(Suite::getBasePrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null);
            int count = branchSuites.size();
            return BranchResponseDTO.from(b, minPrice, count);
        }).toList());

        // Unificar con propiedades aprobadas del marketplace (anfitriones externos como 'waos')
        Set<String> existingSlugs = branches.stream()
                .map(b -> b.getSlug().toLowerCase())
                .collect(Collectors.toSet());

        List<Property> approvedProperties = propertyRepository.findByStatus("APPROVED");
        for (Property p : approvedProperties) {
            if (p.getSlug() != null && !existingSlugs.contains(p.getSlug().toLowerCase())) {
                List<Suite> propSuites = activeSuites.stream()
                        .filter(s -> p.getId().equals(s.getPropertyId()))
                        .toList();
                BigDecimal minPrice = propSuites.stream()
                        .map(Suite::getBasePrice)
                        .filter(Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null);
                Branch b = Branch.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .slug(p.getSlug())
                        .zone(p.getCity() != null ? p.getCity() : "Cartagena")
                        .tagline(p.getShortDescription() != null ? p.getShortDescription() : p.getName())
                        .description(p.getDescription())
                        .address(p.getAddress())
                        .mainImage(p.getMainImage())
                        .active(true)
                        .build();
                dtoList.add(BranchResponseDTO.from(b, minPrice, propSuites.size()));
            }
        }

        return dtoList;
    }

    public BranchResponseDTO getBranchBySlug(String slug) {
        String lower = slug.toLowerCase();
        Optional<Branch> branchOpt = branchRepository.findBySlug(lower);
        if (branchOpt.isPresent()) {
            Branch branch = branchOpt.get();
            List<Suite> branchSuites = suiteRepository.findByBranchAndActiveTrue(branch.getId());
            BigDecimal minPrice = branchSuites.stream()
                    .map(Suite::getBasePrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null);
            int count = branchSuites.size();
            return BranchResponseDTO.from(branch, minPrice, count);
        }

        Property property = propertyRepository.findBySlug(lower)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal o alojamiento no encontrado: " + slug));

        List<Suite> propSuites = suiteRepository.findByPropertyId(property.getId()).stream()
                .filter(s -> !Boolean.FALSE.equals(s.getActive()))
                .toList();
        BigDecimal minPrice = propSuites.stream()
                .map(Suite::getBasePrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);

        Branch b = Branch.builder()
                .id(property.getId())
                .name(property.getName())
                .slug(property.getSlug())
                .zone(property.getCity())
                .tagline(property.getShortDescription() != null ? property.getShortDescription() : property.getName())
                .description(property.getDescription())
                .address(property.getAddress())
                .mainImage(property.getMainImage())
                .active(true)
                .build();
        return BranchResponseDTO.from(b, minPrice, propSuites.size());
    }

    public Branch getBranchById(String id) {
        return branchRepository.findById(id)
                .or(() -> propertyRepository.findById(id).map(p -> Branch.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .slug(p.getSlug())
                        .zone(p.getCity())
                        .address(p.getAddress())
                        .mainImage(p.getMainImage())
                        .active(true)
                        .build()
                ))
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada con id: " + id));
    }
}
