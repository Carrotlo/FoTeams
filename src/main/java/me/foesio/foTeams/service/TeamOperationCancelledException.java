package me.foesio.foTeams.service;

/** A cancellable FoTeams pre-event rejected the operation. */
public final class TeamOperationCancelledException extends RuntimeException {
    public TeamOperationCancelledException() {
        super("cancelled by event");
    }
}
