package de.z0rdak.yawp.handler.flags;

public enum FlagState {
    ALLOWED(true),
    DENIED(false),
    UNDEFINED();

    private final boolean value;
    private final boolean isDefined;

    FlagState() {
        this.value = false;
        this.isDefined = false;
    }

    FlagState(boolean value) {
        this.value = value;
        this.isDefined = true;
    }

    public static FlagState from(boolean value) {
        return value ? ALLOWED : DENIED;
    }

    public boolean isDefined() {
        return isDefined;
    }

    public boolean isTrue() {
        return value;
    }

    public boolean isFalse() {
        return !value;
    }

    public FlagState and(FlagState other) {
        if (!isDefined()) {
            return other;
        }
        if (!other.isDefined()) {
            return this;
        }
        return value && other.value ? ALLOWED : DENIED;
    }

    public FlagState or(FlagState other) {
        if (!isDefined()) {
            return other;
        }
        if (!other.isDefined()) {
            return this;
        }
        return value || other.value ? ALLOWED : DENIED;
    }

    public FlagState not() {
        if (!isDefined()) {
            return this;
        }
        return value ? DENIED : ALLOWED;
    }

}
