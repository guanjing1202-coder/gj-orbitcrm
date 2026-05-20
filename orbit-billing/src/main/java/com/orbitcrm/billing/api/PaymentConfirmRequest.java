package com.orbitcrm.billing.api;

import javax.validation.constraints.NotBlank;

public class PaymentConfirmRequest {
    @NotBlank
    private String paymentChannel;
    private String paymentNo;
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
