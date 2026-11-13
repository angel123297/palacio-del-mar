import 'package:flutter/material.dart';

class PaymentWidget extends StatelessWidget {
  final String planType;

  const PaymentWidget({super.key, required this.planType});

  @override
  Widget build(BuildContext context) {
    bool showWhatsApp = planType != 'mensual' && planType != 'anual';

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        ElevatedButton(
          onPressed: () {},
          child: const Text('Pago con Tarjeta'),
        ),
        const SizedBox(height: 10),
        ElevatedButton(
          onPressed: () {},
          child: const Text('Pago con PSE'),
        ),
        const SizedBox(height: 10),
        if (showWhatsApp)
          ElevatedButton(
            onPressed: () {},
            style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
            child: const Text('Pago por WhatsApp', style: TextStyle(color: Colors.white)),
          ),
      ],
    );
  }
}
