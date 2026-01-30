package com.smartorder.ws;

import com.smartorder.api.dto.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WsPublisher {
  private final SimpMessagingTemplate template;

  public WsPublisher(SimpMessagingTemplate template) {
    this.template = template;
  }

  public void publish(String type, Object payload) {
    template.convertAndSend("/topic/events", new WsEvent(type, payload));
  }
}
