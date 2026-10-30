import 'package:flutter_test/flutter_test.dart';
import 'package:palacio_del_mar_mobile/features/suites/suite_zone_filter.dart';

void main() {
  group('SuiteZoneFilter', () {
    final sampleSuites = [
      {
        'id': 'suite-1',
        'title': 'Suite Virrey',
        'zone': 'centro-historico',
        'zoneLabel': 'Centro Histórico',
      },
      {
        'id': 'suite-2',
        'title': 'Suite Bohemio',
        'zone': 'getsemani',
        'zoneLabel': 'Getsemaní',
      },
      {
        'id': 'suite-3',
        'title': 'Suite Caribe',
        'zone': 'bocagrande',
        'zoneLabel': 'Bocagrande',
      },
    ];

    test('extractZones construye catálogo completo con Todas y slugs reales', () {
      final zones = SuiteZoneFilter.extractZones(sampleSuites);
      expect(zones['Todas'], 'Todas');
      expect(zones['centro-historico'], 'Centro Histórico');
      expect(zones['getsemani'], 'Getsemaní');
      expect(zones['bocagrande'], 'Bocagrande');
      expect(zones.length, 4);
    });

    test('filterSuites con Todas retorna todas las suites', () {
      final filtered = SuiteZoneFilter.filterSuites(
        suites: sampleSuites,
        selectedZone: 'Todas',
      );
      expect(filtered.length, 3);
    });

    test('filterSuites por slug específico retorna solo la sede seleccionada', () {
      final filtered = SuiteZoneFilter.filterSuites(
        suites: sampleSuites,
        selectedZone: 'centro-historico',
      );
      expect(filtered.length, 1);
      expect(filtered.first['id'], 'suite-1');
      expect(filtered.first['zone'], 'centro-historico');
    });

    test('filterSuites combina filtro de zona y disponibilidad', () {
      final filtered = SuiteZoneFilter.filterSuites(
        suites: sampleSuites,
        selectedZone: 'centro-historico',
        availableSuiteIds: {'suite-2', 'suite-3'},
      );
      expect(filtered, isEmpty);

      final availableFiltered = SuiteZoneFilter.filterSuites(
        suites: sampleSuites,
        selectedZone: 'centro-historico',
        availableSuiteIds: {'suite-1'},
      );
      expect(availableFiltered.length, 1);
      expect(availableFiltered.first['id'], 'suite-1');
    });
  });
}
