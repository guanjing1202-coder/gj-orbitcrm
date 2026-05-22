package com.orbitcrm.billing.api;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingOrderCreateRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNegativePeriodMonths() {
        BillingOrderCreateRequest request = validRequest();
        request.setPeriodMonths(-1);

        Set<ConstraintViolation<BillingOrderCreateRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "periodMonths"));
    }

    private BillingOrderCreateRequest validRequest() {
        BillingOrderCreateRequest request = new BillingOrderCreateRequest();
        request.setTenantCode("demo-company");
        request.setPlanCode("professional");
        request.setOrderType("RENEW");
        request.setPeriodMonths(1);
        return request;
    }

    private boolean hasViolation(Set<ConstraintViolation<BillingOrderCreateRequest>> violations, String propertyName) {
        for (ConstraintViolation<BillingOrderCreateRequest> violation : violations) {
            if (propertyName.equals(violation.getPropertyPath().toString())) {
                return true;
            }
        }
        return false;
    }
}
