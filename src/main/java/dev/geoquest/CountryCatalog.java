package dev.geoquest;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CountryCatalog {
    public record Country(String code, String name, String capital, String region, String flag) {}
    private final List<Country> countries;
    public CountryCatalog() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(new ClassPathResource("countries.csv").getInputStream(), StandardCharsets.UTF_8))) {
            countries = reader.lines().map(line -> {
                var c = line.split(";");
                String flag = c[0].codePoints().mapToObj(ch -> new String(Character.toChars(0x1F1E6 + ch - 'A'))).reduce("", String::concat);
                return new Country(c[0], c[1], c[2], c[3], flag);
            }).toList();
        }
    }
    public List<Country> all() { return countries; }
}
