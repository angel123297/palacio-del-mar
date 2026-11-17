package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.dto.SuiteTypeDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuiteService {

    private final SuiteRepository suiteRepository;
    private final BranchRepository branchRepository;
    private final PropertyRepository propertyRepository;
    private final ExperienceRepository experienceRepository;
    private final PromotionRepository promotionRepository;
    private final PricingService pricingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DynamicPricingService dynamicPricingService;

    private Map<String, Branch> getBranchMap() {
        return branchRepository.findAll().stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (a, b) -> a));
    }

    @Cacheable(value = "suites", key = "#branchFilter != null ? #branchFilter : 'all'")
    public List<SuiteResponseDTO> getAllActiveSuites(String branchFilter) {
        Map<String, Branch> branchMap = getBranchMap();
        Map<String, Property> propMap = propertyRepository.findAll().stream()
                .collect(Collectors.toMap(Property::getId, Function.identity(), (a, b) -> a));

        List<Suite> suites = suiteRepository.findByActiveTrue();
        if (suites == null || suites.isEmpty()) {
            suites = suiteRepository.findAll().stream()
                    .filter(s -> !Boolean.FALSE.equals(s.getActive()))
                    .toList();
        }
        suites = suites.stream()
                .filter(s -> {
                    if (s.getPropertyId() == null) {
                        return true;
                    }
                    Property p = propMap.get(s.getPropertyId());
                    return p != null && !Boolean.FALSE.equals(p.getActive()) && "APPROVED".equalsIgnoreCase(p.getStatus());
                })
                .toList();

        if (branchFilter != null && !branchFilter.isBlank()) {
            String lower = branchFilter.trim().toLowerCase();
            Optional<Branch> targetBranch = branchRepository.findById(branchFilter)
                    .or(() -> branchRepository.findBySlug(lower));
            Optional<Property> targetProp = propertyRepository.findById(branchFilter)
                    .or(() -> propertyRepository.findBySlug(lower));

            suites = suites.stream()
                    .filter(s -> {
                        if (targetBranch.isPresent() && targetBranch.get().getId().equals(s.getBranch())) {
                            return true;
                        }
                        if (targetProp.isPresent() && targetProp.get().getId().equals(s.getPropertyId())) {
                            return true;
                        }
                        return false;
                    })
                    .toList();
        }

        return suites.stream()
                .map(s -> {
                    Branch b = branchMap.get(s.getBranch());
                    if (b == null && s.getPropertyId() != null && propMap.containsKey(s.getPropertyId())) {
                        Property p = propMap.get(s.getPropertyId());
                        b = Branch.builder()
                                .id(p.getId())
                                .name(p.getName())
                                .slug(p.getSlug())
                                .zone(p.getCity())
                                .address(p.getAddress())
                                .mainImage(p.getMainImage())
                                .active(true)
                                .build();
                    }
                    return SuiteResponseDTO.from(s, b);
                })
                .collect(Collectors.toList());
    }

    public List<SuiteResponseDTO> getSuitesByBranch(String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .or(() -> branchRepository.findBySlug(branchId.toLowerCase()))
                .orElse(null);
        String targetId = branch != null ? branch.getId() : branchId;

        Map<String, Property> propMap = propertyRepository.findAll().stream()
                .collect(Collectors.toMap(Property::getId, Function.identity(), (a, b) -> a));

        List<Suite> branchSuites = suiteRepository.findByBranchAndActiveTrue(targetId);
        if (branchSuites == null || branchSuites.isEmpty()) {
            branchSuites = suiteRepository.findAll().stream()
                    .filter(s -> targetId.equals(s.getBranch()) && !Boolean.FALSE.equals(s.getActive()))
                    .toList();
        }
        return branchSuites.stream()
                .filter(s -> {
                    if (s.getPropertyId() == null) {
                        return true;
                    }
                    Property p = propMap.get(s.getPropertyId());
                    return p != null && !Boolean.FALSE.equals(p.getActive()) && "APPROVED".equalsIgnoreCase(p.getStatus());
                })
                .map(s -> SuiteResponseDTO.from(s, branch))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "suite_detail", key = "#idOrSlug.toLowerCase()")
    public SuiteResponseDTO getSuiteByIdOrSlug(String idOrSlug) {
        String lower = idOrSlug.trim().toLowerCase();
        Suite suite = suiteRepository.findById(idOrSlug)
                .or(() -> suiteRepository.findBySlug(lower))
                .or(() -> {
                    String alias = AvailabilityService.SLUG_ALIASES.get(lower);
                    return alias != null ? suiteRepository.findById(alias).or(() -> suiteRepository.findBySlug(alias)) : Optional.empty();
                })
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada con identificador: " + idOrSlug));
        if (suite.getPropertyId() != null) {
            Property p = propertyRepository.findById(suite.getPropertyId()).orElse(null);
            if (p == null || Boolean.FALSE.equals(p.getActive()) || !"APPROVED".equalsIgnoreCase(p.getStatus())) {
                throw new IllegalArgumentException("Suite no disponible o la propiedad no está aprobada: " + idOrSlug);
            }
        }
        Branch branch = suite.getBranch() != null ? branchRepository.findById(suite.getBranch()).orElse(null) : null;
        if (branch == null && suite.getPropertyId() != null) {
            branch = propertyRepository.findById(suite.getPropertyId())
                    .map(p -> Branch.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .slug(p.getSlug())
                            .zone(p.getCity())
                            .address(p.getAddress())
                            .mainImage(p.getMainImage())
                            .active(true)
                            .build()
                    ).orElse(null);
        }
        return SuiteResponseDTO.from(suite, branch);
    }

    @Cacheable(value = "suite_types")
    public List<SuiteTypeDTO> getSuiteTypes() {
        Map<String, Property> propMap = propertyRepository.findAll().stream()
                .collect(Collectors.toMap(Property::getId, Function.identity(), (a, b) -> a));

        List<Suite> activeSuites = suiteRepository.findByActiveTrue().stream()
                .filter(s -> {
                    if (s.getPropertyId() == null) {
                        return true;
                    }
                    Property p = propMap.get(s.getPropertyId());
                    return p != null && !Boolean.FALSE.equals(p.getActive()) && "APPROVED".equalsIgnoreCase(p.getStatus());
                })
                .toList();
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

        String rawSuiteId = request.getSuiteId().trim();
        String lowerSuiteId = rawSuiteId.toLowerCase();
        Suite suite = suiteRepository.findById(rawSuiteId)
                .or(() -> suiteRepository.findBySlug(lowerSuiteId))
                .or(() -> {
                    String alias = AvailabilityService.SLUG_ALIASES.get(lowerSuiteId);
                    return alias != null ? suiteRepository.findById(alias).or(() -> suiteRepository.findBySlug(alias)) : Optional.empty();
                })
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada: " + request.getSuiteId()));

        if (suite.getPropertyId() != null) {
            Property p = propertyRepository.findById(suite.getPropertyId()).orElse(null);
            if (p == null || Boolean.FALSE.equals(p.getActive()) || !"APPROVED".equalsIgnoreCase(p.getStatus())) {
                throw new IllegalArgumentException("Suite no disponible o la propiedad no está aprobada: " + request.getSuiteId());
            }
        }

        if (request.getCheckIn() == null || request.getCheckOut() == null || !request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new IllegalArgumentException("La fecha de check-out debe ser estrictamente posterior a la fecha de check-in.");
        }
        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());

        BigDecimal branchPromoPercent = findActiveSuitePromotion(suite, request.getCheckIn(), request.getCheckOut());
        Map<LocalDate, DynamicPricingService.Adjustment> dynamicAdjustments = dynamicPricingService != null
                ? dynamicPricingService.adjustmentsFor(suite, request.getCheckIn(), request.getCheckOut(), null)
                : Map.of();
        PricingService.PricingResult result = (dynamicAdjustments != null && !dynamicAdjustments.isEmpty())
                ? pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent, DynamicPricingService.asFactorFunction(dynamicAdjustments))
                : pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);

        BigDecimal subtotal = result.subtotal();

        BigDecimal experiencesTotal = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIncludeExperiences()) && request.getExperienceIds() != null) {
            for (String expId : request.getExperienceIds()) {
                if (expId != null && !expId.isBlank()) {
                    Optional<Experience> exp = experienceRepository.findById(expId);
                    if (exp.isPresent()) {
                        Experience e = exp.get();
                        if (Boolean.TRUE.equals(e.getActive()) && Boolean.TRUE.equals(e.getAvailable()) && e.getPrice() != null) {
                            experiencesTotal = experiencesTotal.add(e.getPrice());
                        }
                    }
                }
            }
        }

        int totalOccupants = (request.getGuests() != null ? request.getGuests() : 1)
                + (request.getChildren() != null ? request.getChildren() : 0);
        BigDecimal extraGuestsFee = BigDecimal.ZERO;
        if (totalOccupants > 2) {
            extraGuestsFee = new BigDecimal("120000")
                    .multiply(BigDecimal.valueOf(totalOccupants - 2))
                    .multiply(BigDecimal.valueOf(nights));
        }

        BigDecimal vipExtrasTotal = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getAirportPickup())) {
            vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("150000"));
        }
        if (Boolean.TRUE.equals(request.getPetFriendly())) {
            vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("80000"));
        }
        if (request.getFoodOrders() != null) {
            for (var food : request.getFoodOrders()) {
                if (food != null) {
                    BigDecimal foodPrice = BookingService.resolveServerFoodPrice(
                            food.getFoodName() != null ? food.getFoodName() : food.getDishName(),
                            food.getPrice()
                    );
                    if (Boolean.TRUE.equals(food.getIncludesBreakfastCombo())) {
                        foodPrice = foodPrice.add(new BigDecimal("35000"));
                    }
                    vipExtrasTotal = vipExtrasTotal.add(foodPrice);
                }
            }
        }

        BigDecimal roomTotal = result.total();
        BigDecimal guaranteeDeposit = new BigDecimal("100000");
        BigDecimal total = roomTotal.add(experiencesTotal).add(extraGuestsFee).add(vipExtrasTotal);
        BigDecimal totalWithDeposit = total.add(guaranteeDeposit);

        CalculatePriceResponseDTO.DatesInfo datesInfo = CalculatePriceResponseDTO.DatesInfo.builder()
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .nights((int) nights)
                .build();

        CalculatePriceResponseDTO.LineItem lineItem = CalculatePriceResponseDTO.LineItem.builder()
                .unitPrice(suite.getBasePrice())
                .nights((int) nights)
                .amount(subtotal)
                .season("mixed")
                .build();

        return CalculatePriceResponseDTO.builder()
                .suiteId(suite.getId())
                .dates(datesInfo)
                .nightlyPrice(suite.getBasePrice())
                .subtotal(subtotal)
                .roomTotal(roomTotal)
                .extraGuestsFee(extraGuestsFee)
                .guaranteeDeposit(guaranteeDeposit)
                .vipExtrasTotal(vipExtrasTotal)
                .experiencesTotal(experiencesTotal)
                .discount(result.discount())
                .discountReason(result.discount().compareTo(BigDecimal.ZERO) > 0 ? "Promoción / Larga estadía" : null)
                .dynamicPricingNotes(new ArrayList<>(DynamicPricingService.describe(dynamicAdjustments)))
                .total(total)
                .totalWithDeposit(totalWithDeposit)
                .lines(List.of(lineItem))
                .build();
    }

    private BigDecimal findActiveSuitePromotion(Suite suite, LocalDate checkIn, LocalDate checkOut) {
        if (suite == null || checkIn == null || checkOut == null) return BigDecimal.ZERO;
        Set<String> targets = new LinkedHashSet<>();
        if (suite.getBranch() != null && !suite.getBranch().isBlank()) targets.add(suite.getBranch());
        if (suite.getPropertyId() != null && !suite.getPropertyId().isBlank()) targets.add(suite.getPropertyId());
        targets.add("all");

        Map<String, Promotion> unique = new LinkedHashMap<>();
        List<Promotion> batch = promotionRepository.findByBranchInAndActiveTrue(targets);
        if (batch != null && !batch.isEmpty()) {
            for (Promotion p : batch) {
                if (p != null) unique.put(p.getId() != null ? p.getId() : String.valueOf(System.identityHashCode(p)), p);
            }
        } else {
            for (String target : targets) {
                List<Promotion> found = promotionRepository.findByBranchAndActiveTrue(target);
                if (found != null) {
                    for (Promotion p : found) {
                        if (p != null) unique.put(p.getId() != null ? p.getId() : String.valueOf(System.identityHashCode(p)), p);
                    }
                }
            }
        }

        Integer maxDiscount = unique.values().stream()
                .filter(p -> p.getDiscountPercent() != null && p.getDiscountPercent() > 0)
                .filter(p -> (p.getStartDate() == null || !p.getStartDate().isAfter(checkOut.minusDays(1))) &&
                             (p.getEndDate() == null || !p.getEndDate().isBefore(checkIn)))
                .map(Promotion::getDiscountPercent)
                .max(Integer::compareTo)
                .orElse(0);
        return BigDecimal.valueOf(maxDiscount);
    }
}
