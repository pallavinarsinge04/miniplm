package com.miniplm.exception;

/** Thrown when a request is valid but breaks a PLM rule (e.g. editing a released part). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
