package dev.geoquest;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {
    private final CountryCatalog catalog;
    private final JdbcTemplate db;
    public GameController(CountryCatalog catalog, JdbcTemplate db) { this.catalog = catalog; this.db = db; }
    public record Start(String mode, String region) {}
    public record Answer(String questionId, String choice) {}
    private String player(String id) {
        try { return UUID.fromString(id).toString(); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный профиль браузера."); }
    }
    @GetMapping("/countries") public List<CountryCatalog.Country> countries() { return catalog.all(); }
    @PostMapping("/quiz") public Quiz.Question start(@RequestBody Start request, @RequestHeader("X-Player-Id") String id, HttpSession session) {
        String player = player(id);
        if (!Set.of("capitals", "flags").contains(Objects.toString(request.mode(), ""))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный режим.");
        var pool = catalog.all().stream().filter(c -> request.region() == null || request.region().isBlank() || c.region().equals(request.region())).toList();
        if (pool.size() < 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недостаточно стран в регионе.");
        synchronized (session) {
            var quiz = new Quiz(request.mode(), pool);
            session.setAttribute("quiz", quiz);
            session.setAttribute("player", player);
            return quiz.question();
        }
    }
    @PostMapping("/quiz/answer") public Quiz.Feedback answer(@RequestBody Answer request, @RequestHeader("X-Player-Id") String id, HttpSession session) {
        synchronized (session) {
            var quiz = (Quiz) session.getAttribute("quiz");
            if (quiz == null || !player(id).equals(session.getAttribute("player"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Начните новую игру.");
            try {
                var feedback = quiz.submit(request.questionId(), request.choice());
                if (feedback.finished()) db.update("INSERT INTO results(player, mode, correct, total) VALUES (?, ?, ?, ?)", player(id), quiz.mode(), quiz.score(), quiz.total());
                return feedback;
            } catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        }
    }
    @GetMapping("/quiz") public Map<String, Object> current(@RequestHeader("X-Player-Id") String id, HttpSession session) {
        synchronized (session) {
            var quiz = (Quiz) session.getAttribute("quiz");
            if (quiz == null || !player(id).equals(session.getAttribute("player"))) return Map.of();
            return quiz.question() == null ? Map.of() : Map.of("question", quiz.question(), "score", quiz.score());
        }
    }
    @GetMapping("/stats") public Map<String, Object> stats(@RequestHeader("X-Player-Id") String id) {
        String player = player(id);
        var summary = db.queryForMap("SELECT COUNT(*) AS games, COALESCE(SUM(correct),0) AS correct, COALESCE(SUM(total),0) AS total FROM results WHERE player=?", player);
        var history = db.queryForList("SELECT mode, correct, total, finished_at FROM results WHERE player=? ORDER BY id DESC LIMIT 10", player);
        return Map.of("summary", summary, "history", history);
    }
}
