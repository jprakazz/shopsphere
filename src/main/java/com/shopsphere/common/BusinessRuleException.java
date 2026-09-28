package com.shopsphere.common;

/** A request that is well-formed but conflicts with the current state (HTTP 409). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
