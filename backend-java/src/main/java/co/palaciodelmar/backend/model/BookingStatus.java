package co.palaciodelmar.backend.model;

import java.util.Arrays;
import java.util.List;

public enum BookingStatus {
    PENDING("confirmed", "cancelled", "expired"),
    CONFIRMED("checked_in", "cancelled", "no_show"),
    CHECKED_IN("checked_out"),
    CHECKED_OUT(), // Terminal state
    CANCELLED(),   // Terminal state
    EXPIRED(),     // Terminal state
    NO_SHOW();     // Terminal state

    private final List<String> allowedTransitions;

    BookingStatus(String... allowedTransitions) {
        this.allowedTransitions = Arrays.asList(allowedTransitions);
    }

    public boolean canTransitionTo(BookingStatus nextState) {
        return allowedTransitions.contains(nextState.name().toLowerCase());
    }

    public boolean canTransitionTo(String nextState) {
        return allowedTransitions.contains(nextState.toLowerCase());
    }
}
