package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.dto.SuiteTypeDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuiteService {

    private final SuiteRepository suiteRepository;
    private final BranchRepository branchRepository;
    private final ExperienceRepository experienceRepository;

    private Map<String, Branch> getBranchMap() {
        return branchRepository.findAll().stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (a, b) -> a));
    }

    public List<SuiteResponseDTO> getAllActiveSuites(String branchFilter) {
        Map<String, Branch> branchMap = getBranchMap();
        List<Suite> suites = suiteRepository.findByActiveTrue();

        if (branchFilter != null && !branchFilter.isBlank()) {
            String lower = branchFilter.trim().toLowerCase();
            Optional<Branch> targetBranch = branchRepository.findById(branchFilter)
                    .or(() -> branchRepository.findBySlug(lower));

            if (targetBranch.isPresent()) {
                String targetId = targetBranch.get().getId();
                suites = suites.stream()
                        .filter(s -> targetId.equals(s.getBranch()))
                        .toList();
            }
        }

        return suites.stream()
                .map(s -> SuiteResponseDTO.from(s, branchMap.get(s.getBranch())))
                .collect(Collectors.toList());
    }

    public List<SuiteResponseDTO> getSuitesByBranch(String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .or(() -> branchRepository.findBySlug(branchId.toLowerCase()))
                .orElse(null);
        String targetId = branch != null ? branch.getId() : branchId;

        return suiteRepository.findByBranchAndActiveTrue(targetId).stream()
                .map(s -> SuiteResponseDTO.from(s, branch))
                .collect(Collectors.toList());
    }

    public SuiteResponseDTO getSuiteByIdOrSlug(String idOrSlug) {
        Suite suite = suiteRepository.findById(idOrSlug)
                .or(() -> suiteRepository.findBySlug(idOrSlug.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada con identificador: " + idOrSlug));
        Branch branch = suite.getBranch() != null ? branchRepository.findById(suite.getBranch()).orElse(null) : null;
        return SuiteResponseDTO.from(suite, branch);
    }

    public List<SuiteTypeDTO> getSuiteTypes() {
        List<Suite> activeSuites = suiteRepository.findByActiveTrue();
        Map<String, List<Suite>> grouped = activeSuites.stream()
                .filter(s -> s.getType() != null)
                .collect(Collectors.groupingBy(Suite::getType));

        return grouped.entrySet().stream()
                .map(e -> {
                    String type = e.getKey();
                    List<Suite> list = e.getValue();
                    BigDecimal minPrice = list.stream()
                            .map(Suite::getBasePrice)
                            .filter(Objects::nonNull)
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    return SuiteTypeDTO.builder()
                            .name(type)
                            .count(list.size())
                            .minPrice(minPrice)
                            .build();
                })
                .sorted(Comparator.comparing(SuiteTypeDTO::getMinPrice))
                .toList();
    }

    public CalculatePriceResponseDTO calculatePrice(CalculatePriceRequest request) {
        if (request.getSuiteId() == null || request.getCheckIn() == null || request.getCheckOut() == null) {
            throw new IllegalArgumentException("Se requieren suiteId, checkIn y checkOut");
        }

        Suite suite = suiteRepository.findById(request.getSuiteId())
                .or(() -> suiteRepository.findBySlug(request.getSuiteId().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada: " + request.getSuiteId()));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            nights = 1;
        }

        BigDecimal nightlyPrice = suite.getBasePrice();
        BigDecimal subtotal = nightlyPrice.multiply(BigDecimal.valueOf(nights));

        BigDecimal experiencesTotal = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIncludeExperiences()) && request.getExperienceIds() != null) {
            for (String expId : request.getExperienceIds()) {
                if (expId != null && !expId.isBlank() && !expId.startsWith("fx-")) {
                    Optional<Experience> exp = experienceRepository.findById(expId);
                    if (exp.isPresent() && exp.get().getPrice() != null) {
                        experiencesTotal = experiencesTotal.add(exp.get().getPrice());
                    }
                }
            }
        }

        BigDecimal total = subtotal.add(experiencesTotal);

        CalculatePriceResponseDTO.DatesInfo datesInfo = CalculatePriceResponseDTO.DatesInfo.builder()
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .nights((int) nights)
                .build();

        CalculatePriceResponseDTO.LineItem lineItem = CalculatePriceResponseDTO.LineItem.builder()
                .unitPrice(nightlyPrice)
                .nights((int) nights)
                .amount(subtotal)
                .season("low")
                .build();

        return CalculatePriceResponseDTO.builder()
                .suiteId(suite.getId())
                .dates(datesInfo)
                .nightlyPrice(nightlyPrice)
                .subtotal(subtotal)
                .experiencesTotal(experiencesTotal)
                .discount(BigDecimal.ZERO)
                .discountReason(null)
                .total(total)
                .lines(List.of(lineItem))
                .build();
    }
}
