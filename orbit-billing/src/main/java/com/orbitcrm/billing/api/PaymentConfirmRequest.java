package com.orbitcrm.billing.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

public class PaymentConfirmRequest {
    @NotBlank
    private String paymentChannel;
    @NotBlank
    private String paymentNo;
    @PositiveOrZero
    private Long amountCent;

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }
}
