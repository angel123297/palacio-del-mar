class SuiteZoneFilter {
  /// Filtra suites por slug de sede y conjunto de ids disponibles.
  /// Ambos filtros son opcionales.
  static List<Map<String, dynamic>> filterSuites({
    required List<Map<String, dynamic>> suites,
    required String selectedZone, // 'Todas' o slug de la sede (ej. 'centro-historico')
    Set<String>? availableSuiteIds,
  }) {
    return suites.where((suite) {
      if (selectedZone != 'Todas') {
        final suiteZone = suite['zone']?.toString();
        if (suiteZone != selectedZone) {
          return false;
        }
      }
      if (availableSuiteIds != null) {
        final suiteId = suite['id']?.toString();
        if (suiteId == null || !availableSuiteIds.contains(suiteId)) {
          return false;
        }
      }
      return true;
    }).toList();
  }

  /// Extrae el mapa ordenado de zonas (slug -> label) desde el catálogo completo.
  /// Siempre incluye 'Todas' -> 'Todas' al inicio.
  static Map<String, String> extractZones(List<Map<String, dynamic>> suites) {
    final result = <String, String>{'Todas': 'Todas'};
    for (final s in suites) {
      final slug = s['zone']?.toString();
      final label = s['zoneLabel']?.toString() ?? slug;
      if (slug != null && slug.trim().isNotEmpty && slug != 'Todas') {
        result[slug] = (label != null && label.trim().isNotEmpty) ? label : slug;
      }
    }
    return result;
  }
}
