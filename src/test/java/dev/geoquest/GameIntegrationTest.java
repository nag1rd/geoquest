package dev.geoquest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GameIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CountryCatalog catalog;
    @Test void completeRoundIsScoredOnceAndIsolatedByPlayer() throws Exception {
        var session = new MockHttpSession();
        String player = UUID.randomUUID().toString();
        var start = mvc.perform(post("/api/quiz").session(session).header("X-Player-Id", player).contentType("application/json").content("{\"mode\":\"capitals\",\"region\":\"Океания\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode question = mapper.readTree(start.getResponse().getContentAsString());
        Set<String> prompts = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            assertThat(question.has("answer")).isFalse();
            assertThat(question.get("options").size()).isEqualTo(4);
            assertThat(prompts.add(question.get("prompt").asText())).isTrue();
            String prompt = question.get("prompt").asText();
            String correct = catalog.all().stream().filter(c -> prompt.contains("«" + c.name() + "»")).findFirst().orElseThrow().capital();
            String body = mapper.writeValueAsString(Map.of("questionId", question.get("id").asText(), "choice", correct));
            mvc.perform(post("/api/quiz/answer").session(session).header("X-Player-Id", player).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true)).andExpect(jsonPath("$.finished").value(i == 3));
            mvc.perform(post("/api/quiz/answer").session(session).header("X-Player-Id", player).contentType("application/json").content(body)).andExpect(status().isConflict());
            var next = mvc.perform(get("/api/quiz").session(session).header("X-Player-Id", player)).andReturn();
            question = mapper.readTree(next.getResponse().getContentAsString()).get("question");
        }
        mvc.perform(get("/api/stats").header("X-Player-Id", player)).andExpect(jsonPath("$.summary.GAMES").value(1)).andExpect(jsonPath("$.summary.CORRECT").value(4));
        mvc.perform(get("/api/stats").header("X-Player-Id", UUID.randomUUID().toString())).andExpect(jsonPath("$.summary.GAMES").value(0));
    }
    @Test void validatesModeRegionAndAnswer() throws Exception {
        var session = new MockHttpSession(); String player = UUID.randomUUID().toString();
        for (String body : List.of("{}", "{\"mode\":\"other\"}", "{\"mode\":\"flags\",\"region\":\"unknown\"}"))
            mvc.perform(post("/api/quiz").session(session).header("X-Player-Id", player).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        var response = mvc.perform(post("/api/quiz").session(session).header("X-Player-Id", player).contentType("application/json").content("{\"mode\":\"flags\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").isNotEmpty()).andReturn();
        String id = mapper.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/quiz/answer").session(session).header("X-Player-Id", player).contentType("application/json").content(mapper.writeValueAsString(Map.of("questionId", id, "choice", "invalid")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/quiz").session(session).header("X-Player-Id", player)).andExpect(jsonPath("$.question.number").value(1));
    }
    @Test void wrongAnswerDoesNotEarnPoints() throws Exception {
        var quiz = new Quiz("flags", catalog.all());
        var question = quiz.question();
        String right = catalog.all().stream().filter(c -> c.flag().equals(question.flag())).findFirst().orElseThrow().name();
        String wrong = question.options().stream().filter(a -> !a.equals(right)).findFirst().orElseThrow();
        var feedback = quiz.submit(question.id(), wrong);
        assertThat(feedback.correct()).isFalse(); assertThat(feedback.score()).isZero(); assertThat(feedback.answer()).isEqualTo(right);
    }
}
