import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'r') as f:
    content = f.read()

add_exp_search = """        Suite suite = suiteRepository.findById(booking.getSuite()).orElse(null);
        BigDecimal pricePerNight = booking.getPricePerNight() != null ? booking.getPricePerNight() : (suite != null ? suite.getBasePrice() : BigDecimal.ZERO);
        int nights = booking.getNights() != null ? booking.getNights() : 1;
        BigDecimal roomTotal = pricePerNight.multiply(BigDecimal.valueOf(nights));

        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }

        BigDecimal newTotal = roomTotal.add(expTotal);"""

add_exp_replace = """        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }

        BigDecimal roomTotal = booking.getSubtotal() != null ? booking.getSubtotal().add(booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO).subtract(booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal newTotal = roomTotal.add(expTotal);"""

content = content.replace(add_exp_search, add_exp_replace)

rem_exp_search = """        Suite suite = suiteRepository.findById(booking.getSuite()).orElse(null);
        BigDecimal pricePerNight = booking.getPricePerNight() != null ? booking.getPricePerNight() : (suite != null ? suite.getBasePrice() : BigDecimal.ZERO);
        int nights = booking.getNights() != null ? booking.getNights() : 1;
        BigDecimal roomTotal = pricePerNight.multiply(BigDecimal.valueOf(nights));

        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }

        BigDecimal newTotal = roomTotal.add(expTotal);"""

content = content.replace(rem_exp_search, add_exp_replace)

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'w') as f:
    f.write(content)
