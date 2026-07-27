package com.gurukul.chat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
	private final StompSubscribeAuthorizationInterceptor stompSubscribeAuthorizationInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// No SecurityConfig change needed here - the handshake stays under the existing
		// anyRequest().permitAll() fallthrough. Real auth happens on the STOMP CONNECT frame via
		// StompAuthChannelInterceptor, one layer above this HTTP-level handshake.
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor, stompSubscribeAuthorizationInterceptor);
	}

}
