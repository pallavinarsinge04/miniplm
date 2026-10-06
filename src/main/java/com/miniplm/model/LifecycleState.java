package com.miniplm.model;

/**
 * Lifecycle of a part revision (similar to a Windchill lifecycle).
 *
 *   IN_WORK -> UNDER_REVIEW -> APPROVED -> RELEASED
 *                   |
 *                   +-> back to IN_WORK (rejected)
 */
public enum LifecycleState {
    IN_WORK, UNDER_REVIEW, APPROVED, RELEASED;

    public boolean canMoveTo(LifecycleState target) {
        return switch (this) {
            case IN_WORK      -> target == UNDER_REVIEW;
            case UNDER_REVIEW -> target == APPROVED || target == IN_WORK;
            case APPROVED     -> target == RELEASED;
            case RELEASED     -> false;   // final: create a new revision instead
        };
    }

    /** Only In Work revisions can be edited. */
    public boolean isEditable() {
        return this == IN_WORK;
    }
}
