import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'r') as f:
    content = f.read()

# Fix C5 (Guest reservation hijacking)
old_guest_logic = r"""        if (userId == null || userId.isBlank()) \{
            String rawEmail = request.getGuestEmail\(\) == null \? "" : request.getGuestEmail\(\).trim\(\);
            String rawName = request.getGuestName\(\) == null \? "" : request.getGuestName\(\).trim\(\);
            if \(rawName.isEmpty\(\) \|\| rawEmail.isEmpty\(\)\) \{
                throw new IllegalArgumentException\("Por favor completa tu nombre y correo electrónico"\);
            \}
            if \(!rawEmail.matches\("\^\[\^\\\\s@\]\+@\[\^\\\\s@\]\+\\\\\.\[\^\\\\s@\]\+\$"\)\) \{
                throw new IllegalArgumentException\("Ingresa un correo electrónico válido"\);
            \}
            String email = rawEmail.toLowerCase\(\);
            User guest = userRepository.findByEmail\(email\).orElseGet\(\(\) -> \{
                User u = User.builder\(\)
                        .name\(request.getGuestName\(\) != null \? request.getGuestName\(\) : "Huésped"\)
                        .email\(email\)
                        .role\("user"\)
                        .status\("active"\)
                        .emailVerified\(true\)
                        .profile\(User.Profile.builder\(\).phone\(request.getGuestPhone\(\)\).build\(\)\)
                        .createdAt\(Instant.now\(\)\)
                        .updatedAt\(Instant.now\(\)\)
                        .build\(\);
                return userRepository.save\(u\);
            \}\);
            userId = guest.getId\(\);
        \}"""

new_guest_logic = """        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Debes iniciar sesión para realizar una reserva");
        }"""
content = re.sub(old_guest_logic, new_guest_logic, content)

# Fix C3 (Negative price injection)
old_food_logic = r"""        for \(Booking.FoodOrder food : foodOrders\) \{
            if \(food.getPrice\(\) != null\) \{
                vipTotal = vipTotal.add\(food.getPrice\(\)\);
            \} else \{
                food.setPrice\(BigDecimal.valueOf\(80000\)\); // Default price per order
                vipTotal = vipTotal.add\(BigDecimal.valueOf\(80000\)\);
            \}
            if \(Boolean.TRUE.equals\(food.getIncludesBreakfastCombo\(\)\)\) \{
                vipTotal = vipTotal.add\(BigDecimal.valueOf\(35000\)\); // Breakfast combo
            \}
        \}"""

new_food_logic = """        for (Booking.FoodOrder food : foodOrders) {
            food.setPrice(BigDecimal.valueOf(80000));
            vipTotal = vipTotal.add(BigDecimal.valueOf(80000));
            if (Boolean.TRUE.equals(food.getIncludesBreakfastCombo())) {
                vipTotal = vipTotal.add(BigDecimal.valueOf(35000)); // Breakfast combo
            }
        }"""
content = re.sub(old_food_logic, new_food_logic, content)


with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'w') as f:
    f.write(content)
