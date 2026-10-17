package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.HostSuiteRequest;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.dto.PropertyRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final SuiteRepository suiteRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    /**
     * Crea una nueva propiedad asociada al anfitrión autenticado.
     */
    public PropertyDTO createProperty(String ownerId, PropertyRequest req, boolean autoApprove) {
        String baseSlug = toSlug(req.getName());
        String slug = ensureUniqueSlug(baseSlug);

        Property property = Property.builder()
                .ownerId(ownerId)
                .name(req.getName())
                .slug(slug)
                .type(req.getType() != null && !req.getType().isBlank() ? req.getType().toUpperCase() : "HOTEL")
                .city(req.getCity())
                .address(req.getAddress())
                .description(req.getDescription())
                .shortDescription(req.getShortDescription())
                .mainImage(req.getMainImage())
                .images(req.getImages() != null ? req.getImages() : List.of())
                .phone(req.getPhone())
                .externalSiteUrl(req.getExternalSiteUrl())
                .commissionRate(new BigDecimal("0.08")) // 8% fee base del marketplace
                .status(autoApprove ? "APPROVED" : "PENDING")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Property saved = propertyRepository.save(property);
        return toDTO(saved);
    }

    /**
     * Lista las propiedades pertenecientes al anfitrión (aislamiento por ownerId).
     */
    public List<PropertyDTO> getPropertiesByOwner(String ownerId) {
        return propertyRepository.findByOwnerId(ownerId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Obtiene una propiedad específica validando estricto Anti-IDOR.
     * Si no existe o pertenece a otro anfitrión (y no es admin), responde 404 para no revelar existencia.
     */
    public Property getPropertyEntityWithOwnership(String propertyId, String ownerId, boolean isAdmin) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada"));

        if (!isAdmin && (property.getOwnerId() == null || !property.getOwnerId().equals(ownerId))) {
            throw new ResourceNotFoundException("Propiedad no encontrada");
        }
        return property;
    }

    public PropertyDTO getPropertyByOwner(String propertyId, String ownerId, boolean isAdmin) {
        Property property = getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);
        return toDTO(property);
    }

    /**
     * Actualiza una propiedad del anfitrión autenticado (Anti-IDOR).
     */
    public PropertyDTO updateProperty(String propertyId, String ownerId, PropertyRequest req, boolean isAdmin) {
        Property property = getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);

        if (req.getName() != null && !req.getName().isBlank()) {
            property.setName(req.getName());
        }
        if (req.getType() != null && !req.getType().isBlank()) {
            property.setType(req.getType().toUpperCase());
        }
        if (req.getCity() != null && !req.getCity().isBlank()) {
            property.setCity(req.getCity());
        }
        if (req.getAddress() != null && !req.getAddress().isBlank()) {
            property.setAddress(req.getAddress());
        }
        if (req.getDescription() != null) {
            property.setDescription(req.getDescription());
        }
        if (req.getShortDescription() != null) {
            property.setShortDescription(req.getShortDescription());
        }
        if (req.getMainImage() != null) {
            property.setMainImage(req.getMainImage());
        }
        if (req.getImages() != null) {
            property.setImages(req.getImages());
        }
        if (req.getPhone() != null) {
            property.setPhone(req.getPhone());
        }
        if (req.getExternalSiteUrl() != null) {
            property.setExternalSiteUrl(req.getExternalSiteUrl());
        }
        property.setUpdatedAt(Instant.now());

        return toDTO(propertyRepository.save(property));
    }

    /**
     * Cambia el estado de aprobación de una propiedad (ADMIN).
     */
    @Caching(evict = {
            @CacheEvict(value = "suites", allEntries = true),
            @CacheEvict(value = "suite_detail", allEntries = true),
            @CacheEvict(value = "suite_types", allEntries = true)
    })
    public PropertyDTO updatePropertyStatus(String propertyId, String status) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada"));
        property.setStatus(status.toUpperCase());
        property.setUpdatedAt(Instant.now());
        return toDTO(propertyRepository.save(property));
    }

    public List<PropertyDTO> getAllPropertiesForAdmin(String status) {
        List<Property> list;
        if (status != null && !status.isBlank()) {
            list = propertyRepository.findByStatus(status.toUpperCase());
        } else {
            list = propertyRepository.findAll();
        }
        return list.stream().map(this::toDTO).toList();
    }

    /**
     * Lista propiedades aprobadas para huéspedes (público), con filtro opcional por ciudad.
     */
    public List<PropertyDTO> getApprovedProperties(String city) {
        List<Property> list;
        if (city != null && !city.isBlank()) {
            list = propertyRepository.findByCityIgnoreCaseAndStatus(city.trim(), "APPROVED");
        } else {
            list = propertyRepository.findByStatus("APPROVED");
        }
        return list.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(this::toDTO)
                .toList();
    }

    public PropertyDTO getApprovedPropertyById(String id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alojamiento no encontrado"));
        if (!"APPROVED".equalsIgnoreCase(property.getStatus()) || !Boolean.TRUE.equals(property.getActive())) {
            throw new ResourceNotFoundException("Alojamiento no disponible");
        }
        return toDTO(property);
    }

    // --- Gestión de Habitaciones/Suites de la Propiedad (Anti-IDOR) ---

    public List<Suite> getPropertySuitesForHost(String propertyId, String ownerId, boolean isAdmin) {
        getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);
        return suiteRepository.findByPropertyId(propertyId);
    }

    public List<Suite> getApprovedPropertySuites(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada"));
        if (!"APPROVED".equalsIgnoreCase(property.getStatus()) || !Boolean.TRUE.equals(property.getActive())) {
            throw new ResourceNotFoundException("Alojamiento no disponible");
        }
        return suiteRepository.findByPropertyIdAndActiveTrue(propertyId);
    }

    public Suite createSuiteForProperty(String propertyId, String ownerId, HostSuiteRequest req, boolean isAdmin) {
        Property property = getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);

        String suiteSlug = ensureUniqueSuiteSlug(toSlug(property.getName() + " " + req.getName()));

        Suite suite = Suite.builder()
                .propertyId(property.getId())
                .branch(property.getId()) // Compatibilidad
                .source("NATIVE")
                .name(req.getName())
                .slug(suiteSlug)
                .type(req.getType() != null && !req.getType().isBlank() ? req.getType() : "Habitación")
                .description(req.getDescription())
                .shortDescription(req.getShortDescription())
                .basePrice(req.getBasePrice())
                .originalPrice(req.getOriginalPrice() != null ? req.getOriginalPrice() : req.getBasePrice())
                .mainImage(req.getMainImage() != null ? req.getMainImage() : property.getMainImage())
                .images(req.getImages() != null ? req.getImages() : List.of())
                .maxGuests(req.getMaxGuests() != null ? req.getMaxGuests() : 2)
                .maxOccupancy(req.getMaxGuests() != null ? req.getMaxGuests() : 2)
                .size(req.getSize() != null ? req.getSize() : 30)
                .sizeSqm(req.getSize() != null ? req.getSize() : 30)
                .amenities(req.getAmenities() != null ? req.getAmenities() : List.of())
                .totalUnits(req.getTotalUnits() != null ? req.getTotalUnits() : 1)
                .active(req.getActive() != null ? req.getActive() : true)
                .available(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return suiteRepository.save(suite);
    }

    public Suite updateSuiteForProperty(String propertyId, String suiteId, String ownerId, HostSuiteRequest req, boolean isAdmin) {
        getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);

        Suite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        if (!propertyId.equals(suite.getPropertyId()) && !propertyId.equals(suite.getBranch())) {
            throw new ResourceNotFoundException("Habitación no pertenece a esta propiedad");
        }

        if (req.getName() != null && !req.getName().isBlank()) suite.setName(req.getName());
        if (req.getType() != null && !req.getType().isBlank()) suite.setType(req.getType());
        if (req.getDescription() != null) suite.setDescription(req.getDescription());
        if (req.getShortDescription() != null) suite.setShortDescription(req.getShortDescription());
        if (req.getBasePrice() != null) suite.setBasePrice(req.getBasePrice());
        if (req.getOriginalPrice() != null) suite.setOriginalPrice(req.getOriginalPrice());
        if (req.getMainImage() != null) suite.setMainImage(req.getMainImage());
        if (req.getImages() != null) suite.setImages(req.getImages());
        if (req.getMaxGuests() != null) {
            suite.setMaxGuests(req.getMaxGuests());
            suite.setMaxOccupancy(req.getMaxGuests());
        }
        if (req.getSize() != null) {
            suite.setSize(req.getSize());
            suite.setSizeSqm(req.getSize());
        }
        if (req.getAmenities() != null) suite.setAmenities(req.getAmenities());
        if (req.getTotalUnits() != null) suite.setTotalUnits(req.getTotalUnits());
        if (req.getActive() != null) suite.setActive(req.getActive());
        suite.setUpdatedAt(Instant.now());

        return suiteRepository.save(suite);
    }

    public void deleteSuiteForProperty(String propertyId, String suiteId, String ownerId, boolean isAdmin) {
        getPropertyEntityWithOwnership(propertyId, ownerId, isAdmin);

        Suite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        if (!propertyId.equals(suite.getPropertyId()) && !propertyId.equals(suite.getBranch())) {
            throw new ResourceNotFoundException("Habitación no pertenece a esta propiedad");
        }

        suite.setActive(false);
        suite.setAvailable(false);
        suite.setUpdatedAt(Instant.now());
        suiteRepository.save(suite);
    }

    // --- Helpers y Mappers ---

    public PropertyDTO toDTO(Property p) {
        if (p == null) return null;
        long count = p.getId() != null ? suiteRepository.findByPropertyId(p.getId()).size() : 0;
        return PropertyDTO.builder()
                .id(p.getId())
                ._id(p.getId())
                .ownerId(p.getOwnerId())
                .name(p.getName())
                .slug(p.getSlug())
                .type(p.getType())
                .city(p.getCity())
                .address(p.getAddress())
                .description(p.getDescription())
                .shortDescription(p.getShortDescription())
                .mainImage(p.getMainImage())
                .images(p.getImages())
                .commissionRate(p.getCommissionRate())
                .status(p.getStatus())
                .externalSiteUrl(p.getExternalSiteUrl())
                .phone(p.getPhone())
                .active(p.getActive())
                .suitesCount(count)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String toSlug(String input) {
        if (input == null) return "alojamiento-" + UUID.randomUUID().toString().substring(0, 8);
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("").toLowerCase(Locale.ENGLISH);
        return slug.replaceAll("-+", "-");
    }

    private String ensureUniqueSlug(String base) {
        String slug = base;
        int count = 1;
        while (propertyRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + count++;
        }
        return slug;
    }

    private String ensureUniqueSuiteSlug(String base) {
        String slug = base;
        int count = 1;
        while (suiteRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + count++;
        }
        return slug;
    }
}
