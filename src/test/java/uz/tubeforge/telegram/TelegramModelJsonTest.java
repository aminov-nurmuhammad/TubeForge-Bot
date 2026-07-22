package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uz.tubeforge.telegram.model.TgUpdate;
import uz.tubeforge.telegram.model.TgMessage;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramModelJsonTest {
    @Test
    void readsTelegramSnakeCasePayloadsWithJacksonThree() throws Exception {
        String json = """
                {
                  "update_id": 42,
                  "callback_query": {
                    "id": "cb",
                    "from": {"id": 7, "is_bot": false, "first_name": "A", "language_code": "uz"},
                    "message": {"message_id": 9, "chat": {"id": 11, "type": "private"}, "date": 1},
                    "data": "settings"
                  }
                }
                """;
        TgUpdate update = new ObjectMapper().readValue(json, TgUpdate.class);
        assertThat(update.updateId()).isEqualTo(42);
        assertThat(update.callbackQuery().from().firstName()).isEqualTo("A");
        assertThat(update.callbackQuery().message().messageId()).isEqualTo(9);
    }

    @Test
    void extractsReusableTelegramVideoFileId() throws Exception {
        String json = """
                {"message_id": 9, "chat": {"id": 11, "type": "private"}, "date": 1,
                 "video": {"file_id": "video-file-id", "file_unique_id": "stable-id", "file_size": 12345,
                           "width": 1280, "height": 720, "duration": 30}}
                """;
        TgMessage message = new ObjectMapper().readValue(json, TgMessage.class);
        TelegramFileReference reference = TelegramFileReference.from(message).orElseThrow();

        assertThat(reference.kind()).isEqualTo(DeliveryKind.VIDEO);
        assertThat(reference.fileId()).isEqualTo("video-file-id");
        assertThat(reference.fileSize()).isEqualTo(12345);
    }
}
