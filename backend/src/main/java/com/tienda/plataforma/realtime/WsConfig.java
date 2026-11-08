package com.tienda.plataforma.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP en /ws (SockJS para la demo). Salas: /topic/tienda/{id}. */
@Configuration
@EnableWebSocketMessageBroker
public class WsConfig implements WebSocketMessageBrokerConfigurer {

	private final WsAuthInterceptor authInterceptor;
	private final WsTenantInterceptor tenantInterceptor;

	public WsConfig(WsAuthInterceptor authInterceptor, WsTenantInterceptor tenantInterceptor) {
		this.authInterceptor = authInterceptor;
		this.tenantInterceptor = tenantInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").addInterceptors(authInterceptor).setAllowedOriginPatterns("*").withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(tenantInterceptor);
	}
}
