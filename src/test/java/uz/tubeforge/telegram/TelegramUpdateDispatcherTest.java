package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.service.PerformanceMetrics;
import uz.tubeforge.telegram.model.TgChat;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.telegram.model.TgUpdate;
import uz.tubeforge.telegram.model.TgUser;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TelegramUpdateDispatcherTest {

    @Test
    void preservesOneChatOrderWithoutBlockingAnotherChat() throws Exception {
        TelegramUpdateRouter router = mock(TelegramUpdateRouter.class);
        PerformanceMetrics metrics = mock(PerformanceMetrics.class);
        TelegramProperties properties = new TelegramProperties("token", "https://api.telegram.org",
                false, 1, 50_000_000, 2, 50, 0, Duration.ofMillis(10));
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(properties, router, metrics);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondSameChat = new CountDownLatch(1);
        CountDownLatch otherChat = new CountDownLatch(1);
        doAnswer(invocation -> {
            TgUpdate update = invocation.getArgument(0);
            if (update.updateId() == 1) {
                firstStarted.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
            } else if (update.updateId() == 2) {
                secondSameChat.countDown();
            } else if (update.updateId() == 3) {
                otherChat.countDown();
            }
            return null;
        }).when(router).handle(org.mockito.ArgumentMatchers.any());

        try {
            assertThat(dispatcher.dispatch(update(1, 1))).isTrue();
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatcher.dispatch(update(2, 1))).isTrue();
            assertThat(dispatcher.dispatch(update(3, 2))).isTrue();

            assertThat(otherChat.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(secondSameChat.getCount()).isEqualTo(1);
            releaseFirst.countDown();
            assertThat(secondSameChat.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            dispatcher.shutdown();
        }
    }

    private TgUpdate update(long updateId, long chatId) {
        TgUser user = new TgUser(chatId, false, "User", null, null, "en");
        TgMessage message = new TgMessage(updateId, user, new TgChat(chatId, "private", null, null),
                0, "hello", null, null, null, null, null);
        return new TgUpdate(updateId, message, null);
    }
}
