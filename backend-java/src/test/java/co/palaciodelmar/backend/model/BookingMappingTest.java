package co.palaciodelmar.backend.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookingMappingTest {

    @Test
    void verifiesAmountPaidAndAmountRefundedMongoFieldMappings() throws Exception {
        java.lang.reflect.Field paidAmountField = Booking.class.getDeclaredField("paidAmount");
        Field paidAmountAnnotation = paidAmountField.getAnnotation(Field.class);
        assertNotNull(paidAmountAnnotation, "paidAmount must be annotated with @Field");
        assertEquals("amountPaid", paidAmountAnnotation.value(), "@Field must map paidAmount to amountPaid in MongoDB");

        java.lang.reflect.Field refundedAmountField = Booking.class.getDeclaredField("refundedAmount");
        Field refundedAmountAnnotation = refundedAmountField.getAnnotation(Field.class);
        assertNotNull(refundedAmountAnnotation, "refundedAmount must be annotated with @Field");
        assertEquals("amountRefunded", refundedAmountAnnotation.value(), "@Field must map refundedAmount to amountRefunded in MongoDB");
    }

    @Test
    void verifiesBookingBuilderAndAccessorsForAmounts() {
        Booking booking = Booking.builder()
                .paidAmount(new BigDecimal("250000.00"))
                .refundedAmount(new BigDecimal("50000.00"))
                .totalPrice(new BigDecimal("300000.00"))
                .build();

        assertEquals(new BigDecimal("250000.00"), booking.getPaidAmount());
        assertEquals(new BigDecimal("50000.00"), booking.getRefundedAmount());
    }
}
