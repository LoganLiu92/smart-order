package com.smartorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartOrderApplication {
  public static void main(String[] args) {
    SpringApplication.run(SmartOrderApplication.class, args);
  }
}
