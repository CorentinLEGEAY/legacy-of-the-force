package com.legacyoftheforce.core.operation;

public enum OperationPhase {
    PREPARED, HELD, DESTINATION_STAGED, COMMITTED, CLOSED;

    public boolean canTransitionTo(OperationPhase next) {
        return switch (this) {
            case PREPARED -> next == HELD || next == CLOSED;
            case HELD -> next == DESTINATION_STAGED || next == CLOSED;
            case DESTINATION_STAGED -> next == COMMITTED || next == CLOSED;
            case COMMITTED -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}
