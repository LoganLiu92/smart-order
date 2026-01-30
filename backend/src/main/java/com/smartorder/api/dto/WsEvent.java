package com.smartorder.api.dto;

public class WsEvent {
  public String type;
  public Object payload;

  public WsEvent(String type, Object payload) {
    this.type = type;
    this.payload = payload;
  }
}
