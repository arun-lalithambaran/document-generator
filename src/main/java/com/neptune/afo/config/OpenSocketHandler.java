package com.neptune.afo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neptune.afo.enums.SocketMessageEvent;
import com.neptune.afo.model.SocketMessagePayload;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@Slf4j
public class OpenSocketHandler extends TextWebSocketHandler {

  List<SocketSession> onlineUserSessions = new CopyOnWriteArrayList<>();

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message)
      throws InterruptedException, IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    SocketMessagePayload payload =
        objectMapper.readValue(message.getPayload(), SocketMessagePayload.class);
    if (!payload.getRecipients().isEmpty()) {
      Optional<String> fromClient =
          onlineUserSessions.stream()
              .filter(
                  el ->
                      el.getSession()
                          .getId()
                          .equalsIgnoreCase(
                              session.getId())) // && !session.getId().equals(sender.getId())
              .map(SocketSession::getClientId)
              .findFirst();
      fromClient.ifPresent(
          client -> {
            payload.setFrom(client);
            sendMessage(payload);
          });
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    try {
      SocketSession socketSession = getSocketSession(session);
      onlineUserSessions.add(socketSession);
      SocketMessagePayload payload =
          SocketMessagePayload.builder()
              .message(socketSession.getClientId())
              .event(SocketMessageEvent.CLIENT_ID.getValue())
              .from(socketSession.getClientId())
              .recipients(List.of(socketSession.clientId))
              .build();
      sendMessage(payload);
    } catch (Exception e) {
      session.close();
    }
  }

  private SocketSession getSocketSession(WebSocketSession session) {
    String queryString = Objects.requireNonNull(session.getUri()).getQuery();
    String preferredUsername = null;
    if (StringUtils.hasLength(queryString)) {
      String[] split = queryString.split("preferredUsername=");
      preferredUsername = split.length > 0 ? split[1] : "";
      if (preferredUsername.length() < 4) {
        preferredUsername = null;
      } else {
        String finalPreferredUsername = preferredUsername;
        boolean clientIdExist =
            onlineUserSessions.stream()
                .anyMatch(
                    activeSession ->
                        activeSession.getClientId().equalsIgnoreCase(finalPreferredUsername));
        if (clientIdExist) preferredUsername = null;
      }
    }

    return new SocketSession(session, preferredUsername);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    List<SocketSession> sessionList =
        onlineUserSessions.stream()
            .filter(
                socketSession ->
                    socketSession.getSession().getId().equals(session.getId())
                        || !socketSession.getSession().isOpen())
            .toList();
    sessionList.forEach(
        s -> {
          try {
            s.getSession().close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    onlineUserSessions.removeAll(sessionList);
  }

  public void sendMessage(SocketMessagePayload socketMessagePayload) {
    List<WebSocketSession> recipientSessions =
        onlineUserSessions.stream()
            .filter(
                client ->
                    socketMessagePayload.getRecipients().contains(client.getClientId())
                        && client.getSession().isOpen())
            .map(SocketSession::getSession)
            .toList();
    ObjectMapper objectMapper = new ObjectMapper();
    for (WebSocketSession session : recipientSessions) {
      try {
        if (session.isOpen()) {
          socketMessagePayload.setRecipients(List.of());
          session.sendMessage(
              new TextMessage(objectMapper.writeValueAsString(socketMessagePayload)));
        }
      } catch (IOException e) {
        log.error("message send failed");
      }
    }
  }

  @Getter
  public static class SocketSession {
    private final String clientId;
    private final WebSocketSession session;

    public SocketSession(WebSocketSession session, String preferredUsername) {
      this.session = session;
      this.clientId = preferredUsername != null ? preferredUsername : generateRandomClientId();
    }

    private String generateRandomClientId() {
      int firstPart = ThreadLocalRandom.current().nextInt(100, 1000);
      int secondPart = ThreadLocalRandom.current().nextInt(100, 1000);
      return firstPart + "-" + secondPart;
    }
  }
}
