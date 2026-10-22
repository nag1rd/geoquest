package dev.geoquest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.geoquest.CountryCatalog.Country;

public class Quiz {
    public record Question(String id, String prompt, String flag, List<String> options, int number, int total) {}
    public record Feedback(boolean correct, String answer, String explanation, int score, boolean finished) {}
    private final String mode;
    private final List<Country> selected;
    private final List<Country> pool;
    private int index, score;
    private Question current;
    public Quiz(String mode, List<Country> pool) {
        this.mode = mode;
        this.pool = List.copyOf(pool);
        var shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        selected = List.copyOf(shuffled.subList(0, Math.min(10, shuffled.size())));
        createQuestion();
    }
    private String answer(Country country) { return mode.equals("capitals") ? country.capital() : country.name(); }
    private void createQuestion() {
        if (index == selected.size()) { current = null; return; }
        var country = selected.get(index);
        var distractors = new ArrayList<>(pool.stream().map(this::answer).distinct().filter(a -> !a.equals(answer(country))).toList());
        Collections.shuffle(distractors);
        var options = new ArrayList<>(distractors.subList(0, 3));
        options.add(answer(country));
        Collections.shuffle(options);
        current = new Question(UUID.randomUUID().toString(), mode.equals("capitals") ? "Столица страны «" + country.name() + "»?" : "Какой стране принадлежит флаг?", mode.equals("flags") ? country.flag() : null, List.copyOf(options), index + 1, selected.size());
    }
    public Question question() { return current; }
    public Feedback submit(String questionId, String choice) {
        if (current == null || !current.id().equals(questionId)) throw new IllegalStateException("Этот вопрос уже закрыт. Обновите игру.");
        if (!current.options().contains(choice)) throw new IllegalArgumentException("Выберите один из вариантов ответа.");
        var country = selected.get(index);
        boolean correct = answer(country).equals(choice);
        if (correct) score++;
        var feedback = new Feedback(correct, answer(country), country.name() + " · " + country.capital() + " · " + country.region(), score, index + 1 == selected.size());
        index++;
        createQuestion();
        return feedback;
    }
    public String mode() { return mode; }
    public int score() { return score; }
    public int total() { return selected.size(); }
}
