import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/PaymentService.java', 'r') as f:
    content = f.read()

search = """        if (userId != null && !booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para confirmar este pago");
        }

        if ("completed".equals(payment.getStatus())) {"""

replace = """        if (userId != null && !booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para confirmar este pago");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("No se puede confirmar el pago para una reserva en estado " + booking.getStatus());
        }
        if ("pending".equalsIgnoreCase(booking.getStatus()) && booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("El tiempo para pagar esta reserva ha expirado");
        }

        if ("completed".equals(payment.getStatus())) {"""

if search in content:
    content = content.replace(search, replace)
    with open('backend-java/src/main/java/co/palaciodelmar/backend/service/PaymentService.java', 'w') as f:
        f.write(content)
        print("Success")
else:
    print("Not found")

