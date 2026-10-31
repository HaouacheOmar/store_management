package com.store.reporting.websocket;

import com.store.common.security.CurrentUser;
import com.store.common.security.JwtSecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sur /ws. La poignée de main HTTP est publique (un navigateur ne peut pas y mettre d'en-tête) ;
 * l'authentification se fait au CONNECT avec le JWT, et un abonnement n'est accepté que sur le topic
 * de son propre store, avec STOCK_RESTOCK (destinataires des alertes, PRD M10).
 */
// ponytail: broker simple en mémoire = une seule instance de reporting ; relais STOMP (RabbitMQ) pour en avoir plusieurs
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String NOTIFY_PERMISSION = "STOCK_RESTOCK";

    private final JwtDecoder jwtDecoder;

    public WebSocketConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // origine libre : l'authentification passe par le JWT du CONNECT, jamais par un cookie (pas de détournement cross-site)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (stomp == null || stomp.getCommand() == null) return message;
                switch (stomp.getCommand()) {
                    case CONNECT -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
                    case SUBSCRIBE -> checkSubscription(stomp);
                    case SEND -> throw new MessagingException("Envoi client non autorisé");
                    default -> { }
                }
                return message;
            }
        });
    }

    private AbstractAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new MessagingException("JWT manquant");
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            return JwtSecurityConfig.authenticationConverter().convert(jwt);
        } catch (JwtException e) {
            throw new MessagingException("JWT invalide");
        }
    }

    private static void checkSubscription(StompHeaderAccessor stomp) {
        if (!(stomp.getUser() instanceof AbstractAuthenticationToken auth) || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new MessagingException("Non authentifié");
        }
        boolean canNotify = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(NOTIFY_PERMISSION));
        String ownTopic = NotificationPush.topic(CurrentUser.from(jwt).storeId());
        if (!canNotify || !ownTopic.equals(stomp.getDestination())) {
            throw new MessagingException("Abonnement refusé : " + stomp.getDestination());
        }
    }
}
