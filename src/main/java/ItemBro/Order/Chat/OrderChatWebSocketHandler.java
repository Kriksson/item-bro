package ItemBro.Order.Chat;

import ItemBro.Order.DTO.MessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderChatWebSocketHandler extends TextWebSocketHandler {
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final static Logger logger = LoggerFactory.getLogger(OrderChatWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    public OrderChatWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object stateAttribute = session.getAttributes().get(OrderChatSessionState.ATTRIBUTE);

        if (!(stateAttribute instanceof OrderChatSessionState chatSession) || !chatSession.isActive()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Object attribute = session.getAttributes().get("orderId");

        if (!(attribute instanceof Long orderId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64*1024
        );

        sessions.compute(orderId, (id, connections) -> {
            if (connections == null) {
                connections = new ConcurrentHashMap<>();
            }

            connections.put(session.getId(), safeSession);

            return connections;
        });

        if (!chatSession.isActive()) {
            closeSession(safeSession, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        removeSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        removeSession(session);
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    private void removeSession(WebSocketSession session) {
        Object attribute =  session.getAttributes().get("orderId");

        if (!(attribute instanceof Long orderId)) {
            return;
        }

        sessions.computeIfPresent(orderId, (id, connections) -> {
            connections.remove(session.getId());

            if (connections.isEmpty()) {
                return null;
            }

            return connections;
        });
    }

    public void sendMessage(MessageDTO message) {
        Map<String, WebSocketSession> connections = sessions.get(message.orderId());

        if (connections == null) {
            return;
        }

        TextMessage frame;

        try {
            String json = objectMapper.writeValueAsString(
                    Map.of(
                            "type", "message.created",
                            "message", message
                    )
            );
            frame = new TextMessage(json);
        } catch (JsonProcessingException e) {
            logger.error("Не удалось сериализовать сообщение {}, заказ {}", message.id(), message.orderId(), e);
            return;
        }

        for (WebSocketSession session : connections.values()) {
            try {
                Object stateAttribute = session.getAttributes().get(OrderChatSessionState.ATTRIBUTE);
                if (!(stateAttribute instanceof OrderChatSessionState orderChatSessionState)
                        || !orderChatSessionState.isActive()) {
                    closeSession(session, CloseStatus.POLICY_VIOLATION);
                    continue;
                }

                if (!session.isOpen()) {
                    removeSession(session);
                    continue;
                }

                session.sendMessage(frame);
            } catch (IOException | RuntimeException e) {
                logger.warn("Не удалось отправить сообщение {} в WS {}", message.id(), session.getId(), e);
                closeSession(session);
            }
        }
    }

    private void closeSession(WebSocketSession session) {
        closeSession(session, CloseStatus.SERVER_ERROR);
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        removeSession(session);

        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("Не удалось закрыть сессию {}", session.getId(), e);
        }
    }

    public void closeHttpSession(OrderChatSessionState state) {
        for (Map<String, WebSocketSession> connections : sessions.values()) {
            for (WebSocketSession session : connections.values()) {
                Object attribute = session.getAttributes().get(OrderChatSessionState.ATTRIBUTE);
                if (attribute == state) {
                    closeSession(session, CloseStatus.POLICY_VIOLATION);
                }
            }
        }
    }

}
