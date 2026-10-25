import 'package:flutter/material.dart';

class FiltrosWidget extends StatefulWidget {
  final Function(double, int, bool) onFiltrosAplicados;

  const FiltrosWidget({Key? key, required this.onFiltrosAplicados}) : super(key: key);

  @override
  _FiltrosWidgetState createState() => _FiltrosWidgetState();
}

class _FiltrosWidgetState extends State<FiltrosWidget> {
  double _precioMax = 1000000;
  int _huespedes = 1;
  bool _petFriendly = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Filtros Avanzados', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Precio Máx:'),
              Text('\$${_precioMax.toInt()}'),
            ],
          ),
          Slider(
            value: _precioMax,
            min: 50000,
            max: 2000000,
            divisions: 39,
            onChanged: (val) => setState(() => _precioMax = val),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Huéspedes:'),
              DropdownButton<int>(
                value: _huespedes,
                items: [1, 2, 3, 4, 5, 6].map((int value) {
                  return DropdownMenuItem<int>(value: value, child: Text(value.toString()));
                }).toList(),
                onChanged: (val) => setState(() => _huespedes = val ?? 1),
              ),
            ],
          ),
          SwitchListTile(
            title: const Text('Pet Friendly'),
            value: _petFriendly,
            onChanged: (val) => setState(() => _petFriendly = val),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () {
              widget.onFiltrosAplicados(_precioMax, _huespedes, _petFriendly);
              Navigator.pop(context);
            },
            child: const Text('Aplicar Filtros'),
          )
        ],
      ),
    );
  }
}