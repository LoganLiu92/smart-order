package com.smartorder.api.dto;

import com.smartorder.model.PaymentStatus;

public class PaymentUpdateRequest {
  public PaymentStatus paymentStatus;
  public String paidBy;
}
