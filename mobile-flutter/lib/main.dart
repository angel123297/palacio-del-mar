import 'package:flutter/material.dart';

void main() {
  runApp(const MaterialApp(
    debugShowCheckedModeBanner: false,
    home: PalacioDelMarHome(),
  ));
}

class PalacioDelMarHome extends StatelessWidget {
  const PalacioDelMarHome({super.key});

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return Column(
              children: [
                Container(
                  height: screenSize.height * 0.25,
                  width: constraints.maxWidth,
                  color: const Color(0xFF1E3A8A),
                  child: const Center(
                    child: Text(
                      'Palacio del Mar',
                      style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: Container(
                    width: constraints.maxWidth,
                    margin: const EdgeInsets.all(16.0),
                    decoration: BoxDecoration(
                      color: Colors.grey[200],
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.transparent),
                    ),
                    child: const Center(
                      child: Text(
                        'Aquí integraremos el widget del mapa',
                        style: TextStyle(color: Colors.black54),
                      ),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
