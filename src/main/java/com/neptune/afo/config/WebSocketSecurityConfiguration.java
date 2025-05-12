package com.neptune.afo.config;

import com.neptune.afo.service.JwtService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketSecurityConfiguration implements WebSocketMessageBrokerConfigurer {

  @Autowired private JwtService jwtService;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
              List<String> authorization = accessor.getNativeHeader("Authorization");
              if (authorization == null || authorization.isEmpty()) {
                throw new IllegalArgumentException("Missing authorization header");
              }
              String token = authorization.get(0);
              // Validate token here
              if (jwtService.isTokenExpired(token.substring(7))) {
                throw new IllegalArgumentException("Invalid token");
              }
            }
            return message;
          }
        });
  }
}
