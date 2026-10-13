package com.scientia.core.math;

/** A user-facing evaluation error (syntax, domain, division by zero, overflow, unit mismatch...). */
public class MathException extends RuntimeException {
    private final int position;

    public MathException(String message) {
        this(message, -1);
    }

    public MathException(String message, int position) {
        super(message);
        this.position = position;
    }

    /** Character index in the input where the problem was detected, or -1. */
    public int position() {
        return position;
    }
}
