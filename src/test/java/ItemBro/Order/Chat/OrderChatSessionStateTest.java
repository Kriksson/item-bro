package ItemBro.Order.Chat;

import ItemBro.Order.DTO.MessageDTO;
import ItemBro.Order.MessageType;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderChatSessionStateTest {
    @Test
    @DisplayName("WS: инвалидирование сессии между проверкой активности и регистрацией не оставляет соединение")
    void invalidationDuringRegistrationClosesLateConnection() throws Exception {
        var handler = new OrderChatWebSocketHandler(new ObjectMapper());
        var state = new OrderChatSessionState(handler);
        var httpSession = new MockHttpSession();
        httpSession.setAttribute(OrderChatSessionState.ATTRIBUTE, state);
        WebSocketSession socket = session(state, "racing");
        var registrationReached = new CountDownLatch(1);
        var registrationAllowed = new CountDownLatch(1);
        var paused = new AtomicBoolean();
        when(socket.getId()).thenAnswer(invocation -> {
            if (paused.compareAndSet(false, true)) {
                registrationReached.countDown();
                if (!registrationAllowed.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Регистрация не была разблокирована");
                }
            }
            return "racing";
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var registration = executor.submit(() -> {
                try { handler.afterConnectionEstablished(socket); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            assertThat(registrationReached.await(5, TimeUnit.SECONDS)).isTrue();
            httpSession.invalidate();
            assertThat(state.isActive()).isFalse();
            registrationAllowed.countDown();
            registration.get(5, TimeUnit.SECONDS);
            verify(socket).close(CloseStatus.POLICY_VIOLATION);
            // Повторное закрытие группы не должно найти уже удалённое соединение.
            handler.closeHttpSession(state);
            verify(socket, times(1)).close(any(CloseStatus.class));
        } finally {
            registrationAllowed.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("WS: ошибка отправки одному соединению не мешает остальным и исключает сбойное из следующих рассылок")
    void failedConnectionDoesNotInterruptOtherRecipients(boolean runtimeFailure) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var handler = new OrderChatWebSocketHandler(mapper);
        var state = new OrderChatSessionState(handler);
        WebSocketSession broken = session(state, "broken");
        WebSocketSession healthy = session(state, "healthy");
        if (runtimeFailure) {
            doThrow(new IllegalStateException("Соединение закрывается")).when(broken).sendMessage(any());
        } else {
            doThrow(new IOException("Разрыв соединения")).when(broken).sendMessage(any());
        }
        // Даже ошибка закрытия сбойного клиента не должна прервать рассылку.
        doThrow(new IOException("Закрытие не удалось")).when(broken).close(any(CloseStatus.class));
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);
        var message = new MessageDTO(105L, 73L, MessageType.USER, null,
                new UserSellerDTO("alex", UserRole.USER, null), "Привет", Instant.now());
        handler.sendMessage(message);
        handler.sendMessage(message);
        verify(broken, times(1)).sendMessage(any());
        verify(healthy, times(2)).sendMessage(any());
        verify(healthy, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("WS: подключение с уже уничтоженной HTTP-сессией отклоняется до регистрации")
    void rejectsInactiveSessionBeforeRegistration() throws Exception {
        var handler = new OrderChatWebSocketHandler(new ObjectMapper());
        var state = new OrderChatSessionState(handler);
        var httpSession = new MockHttpSession();
        httpSession.setAttribute(OrderChatSessionState.ATTRIBUTE, state);
        httpSession.invalidate();
        WebSocketSession socket = session(state, "late");
        handler.afterConnectionEstablished(socket);
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        verify(socket, never()).getId();
    }

    private WebSocketSession session(OrderChatSessionState state, String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("orderId", 73L, OrderChatSessionState.ATTRIBUTE, state));
        return session;
    }
}
