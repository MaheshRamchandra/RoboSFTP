package org.robo.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads dependency rule metadata with light validation.
 */
public final class DependencyRuleService {

    private DependencyRuleService() {
    }

    public static List<RuleEntry> load(File file, List<String> warnings) throws IOException {
        if (file == null) throw new IOException("Rules file is required");
        if (!file.exists()) throw new IOException("Rules file not found: " + file.getAbsolutePath());

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> list = mapper.readValue(file, new TypeReference<>() {});
        List<RuleEntry> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            String name = text(m.get("Name"));
            String required = text(m.get("Required"));
            String rule = text(m.get("Rule"));
            if (name.isEmpty()) {
                warnings.add("Entry " + i + " missing Name.");
                continue;
            }
            if (required.isEmpty()) {
                warnings.add("Entry " + name + " missing Required flag.");
                continue;
            }
            if (!required.toLowerCase(Locale.ROOT).contains("refer to rule")) {
                warnings.add("Entry " + name + " is not a conditional rule; skipping.");
                continue;
            }
            if (rule.isEmpty()) {
                warnings.add("Entry " + name + " missing rule text.");
                continue;
            }
            result.add(new RuleEntry(name, required, rule));
        }
        return result;
    }

    private static String text(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    public record RuleEntry(String name, String required, String rule) {
    }
}
