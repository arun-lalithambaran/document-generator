package com.neptune.afo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neptune.afo.entity.User;
import com.neptune.afo.enums.SocketMessageEvent;
import com.neptune.afo.exception.UnAuthorizedException;
import com.neptune.afo.model.OnlineUser;
import com.neptune.afo.model.SocketMessagePayload;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
public class SocketService {

  List<SocketSession> onlineUserSessions = new CopyOnWriteArrayList<>();

  @Autowired JwtService jwtService;
  @Autowired UserService userService;

  public void onSocketConnection(WebSocketSession webSocketSession) throws Exception {
    String authToken =
        Objects.requireNonNull(webSocketSession.getUri())
            .getQuery()
            .split("token=")[1]
            .substring(7);
    if (jwtService.isTokenExpired(authToken)) {
      throw new UnAuthorizedException("Token Expired!");
    }
    String username = jwtService.extractUsernameFromToken(authToken);
    User connectedUser = userService.getUser(username);
    if (connectedUser == null) {
      throw new UnAuthorizedException("User not found!");
    }
    SocketSession socketSession = new SocketSession(connectedUser, webSocketSession);
    onlineUserSessions.add(socketSession);
    broadcastOnlineUserDetails(webSocketSession);
  }

  private void broadcastOnlineUserDetails(WebSocketSession webSocketSession) {
    broadCastMessage(
        webSocketSession,
        SocketMessagePayload.builder()
            .message(
                onlineUserSessions.stream()
                    .map(
                        session ->
                            OnlineUser.builder()
                                .email(session.getUser().getEmail())
                                .name(
                                    String.format(
                                        "%s %s",
                                        session.getUser().getFirstName(),
                                        session.getUser().getLastName()))
                                .sessionId(session.getSession().getId())
                                .build())
                    .toList())
            .event(SocketMessageEvent.ONLINE_USERS.getValue())
            .build());
  }

  public void onSocketConnectionClose(WebSocketSession session) {
    onlineUserSessions.removeIf(
        socketSession -> socketSession.getSession().getId().equals(session.getId()));
    broadcastOnlineUserDetails(session);
  }

  public void onMessage(WebSocketSession session, TextMessage message) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      SocketMessagePayload payload =
          objectMapper.readValue(message.getPayload(), SocketMessagePayload.class);
      if (!payload.getRecipients().isEmpty()) {
        payload.setFrom(session.getId());
        sendMessage(payload);
      }
    } catch (JsonProcessingException e) {
      log.error("Message parsing failed!");
    }
  }

  public void sendMessage(SocketMessagePayload socketMessagePayload) {
    List<WebSocketSession> recipientSessions =
        onlineUserSessions.stream()
            .filter(
                session ->
                    socketMessagePayload.getRecipients().contains(session.getSession().getId())
                        && session.getSession().isOpen())
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

  public void broadCastMessage(WebSocketSession sender, SocketMessagePayload payload) {
    List<String> recipients =
        onlineUserSessions.stream()
            .map(SocketSession::getSession)
            .filter(WebSocketSession::isOpen) // && !session.getId().equals(sender.getId())
            .map(WebSocketSession::getId)
            .toList();
    payload.setFrom(sender.getId());
    payload.setRecipients(recipients);
    sendMessage(payload);
  }

  @AllArgsConstructor
  @Getter
  public static class SocketSession {
    private User user;
    private WebSocketSession session;
  }
}
