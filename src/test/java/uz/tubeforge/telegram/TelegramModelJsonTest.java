package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uz.tubeforge.telegram.model.TgUpdate;

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
}
