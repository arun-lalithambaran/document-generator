package com.neptune.afo.enums;

public enum SocketMessageEvent {
  ONLINE_USERS("online_users"),
  CLIENT_ID("client_id"),
  BROADCAST("broadcast");

  private final String value;

  SocketMessageEvent(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
