package com.scientia.core.units;

/** Thrown for invalid unit operations, such as adding a length to a mass. */
public class UnitException extends RuntimeException {
    public UnitException(String message) {
        super(message);
    }
}
