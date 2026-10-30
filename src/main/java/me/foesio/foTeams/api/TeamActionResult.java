package me.foesio.foTeams.api;

/** Result of a requested team action. Database failures are reported separately as SQLException. */
public record TeamActionResult<T>(Status status, T value) {
    public enum Status {
        SUCCESS, NOT_FOUND, INVALID, DENIED, NO_CHANGE, FULL, NOT_INVITED,
        INSUFFICIENT_FUNDS, UNAVAILABLE, CANCELLED, ECONOMY_FAILURE,
        SAVE_FAILED_REFUNDED, COMPENSATION_FAILED
    }

    public boolean successful() { return status == Status.SUCCESS; }
    public static <T> TeamActionResult<T> success(T value) { return new TeamActionResult<>(Status.SUCCESS, value); }
    public static <T> TeamActionResult<T> failure(Status status) { return new TeamActionResult<>(status, null); }
}
