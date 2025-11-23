package org.robo.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RagSpecBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RagSpecBuilder() {
    }

    public static String toJsonArray(List<RagFieldRecord> records) throws JsonProcessingException {
        List<Object> sorted = dedupeAndSort(records).stream()
                .map(RagFieldRecord::toSpecMap)
                .collect(Collectors.toList());
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sorted);
    }

    public static List<RagFieldRecord> parse(String json) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray()) {
            throw new JsonProcessingException("Expected JSON array in LLM response") {};
        }
        List<RagFieldRecord> records = MAPPER.readValue(json, new TypeReference<>() {});
        return dedupeAndSort(records);
    }

    private static List<RagFieldRecord> dedupeAndSort(List<RagFieldRecord> records) {
        return records.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RagFieldRecord::getPosition))
                .collect(Collectors.toList());
    }
}
