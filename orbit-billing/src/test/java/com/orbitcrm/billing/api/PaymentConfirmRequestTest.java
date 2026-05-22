package com.orbitcrm.billing.api;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentConfirmRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankPaymentNo() {
        PaymentConfirmRequest request = validRequest();
        request.setPaymentNo(" ");

        Set<ConstraintViolation<PaymentConfirmRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "paymentNo"));
    }

    @Test
    void rejectsNegativeAmountCent() {
        PaymentConfirmRequest request = validRequest();
        request.setAmountCent(-1L);

        Set<ConstraintViolation<PaymentConfirmRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "amountCent"));
    }

    private PaymentConfirmRequest validRequest() {
        PaymentConfirmRequest request = new PaymentConfirmRequest();
        request.setPaymentChannel("MANUAL");
        request.setPaymentNo("PAY-001");
        request.setAmountCent(9900L);
        return request;
    }

    private boolean hasViolation(Set<ConstraintViolation<PaymentConfirmRequest>> violations, String propertyName) {
        for (ConstraintViolation<PaymentConfirmRequest> violation : violations) {
            if (propertyName.equals(violation.getPropertyPath().toString())) {
                return true;
            }
        }
        return false;
    }
}
