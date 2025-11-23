package org.robo.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Minimal local vector-store-like loader backed by JSON.
 * It filters by RDG and section metadata to simulate retrieval.
 */
public class LocalJsonVectorStore implements RagVectorStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<RagFieldRecord> cache = new ArrayList<>();
    private File currentFile;

    @Override
    public void refresh(File file) throws IOException {
        Objects.requireNonNull(file, "Knowledge base JSON file is required");
        if (!file.exists()) throw new IOException("File not found: " + file.getAbsolutePath());
        try (InputStream is = new java.io.FileInputStream(file)) {
            List<RagFieldRecord> data = readRecords(is);
            cache.clear();
            cache.addAll(data);
            currentFile = file;
        }
    }

    public void refreshFromClasspath(String resourcePath) throws IOException {
        Objects.requireNonNull(resourcePath, "Resource path is required");
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Resource not found: " + resourcePath);
            List<RagFieldRecord> data = readRecords(is);
            cache.clear();
            cache.addAll(data);
            currentFile = null;
        }
    }

    @Override
    public List<RagFieldRecord> retrieve(String rdg) {
        return retrieve(rdg, defaultSections());
    }

    @Override
    public List<RagFieldRecord> retrieve(String rdg, Set<String> sections) {
        Set<String> normalizedSections = normalizeSectionSet(sections);
        String normalizedRdg = rdg == null ? "" : rdg.trim().toLowerCase(Locale.ROOT);
        List<RagFieldRecord> filtered = new ArrayList<>();
        for (RagFieldRecord rec : cache) {
            boolean rdgMatch = normalizedRdg.isEmpty()
                    || safe(rec.getRdg()).isEmpty()
                    || safe(rec.getRdg()).equalsIgnoreCase(normalizedRdg);
            boolean sectionMatch = normalizedSections.isEmpty()
                    || safe(rec.getSection()).isEmpty()
                    || normalizedSections.contains(safe(rec.getSection()).toLowerCase(Locale.ROOT));
            if (rdgMatch && sectionMatch) {
                filtered.add(rec);
            }
        }
        return filtered;
    }

    @Override
    public boolean isLoaded() {
        return !cache.isEmpty();
    }

    @Override
    public String sourceDescription() {
        if (currentFile != null) return currentFile.getAbsolutePath();
        if (cache.isEmpty()) return "none";
        return "classpath resource";
    }

    @Override
    public Set<String> defaultSections() {
        Set<String> sections = new HashSet<>();
        Collections.addAll(sections, "clinicalstaff", "1", "2", "3", "4");
        return sections;
    }

    private Set<String> normalizeSectionSet(Set<String> sections) {
        if (sections == null) return Collections.emptySet();
        return sections.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private List<RagFieldRecord> readRecords(InputStream is) throws IOException {
        Objects.requireNonNull(is, "Input stream is required");
        JsonNode root = mapper.readTree(is);
        if (root == null) throw new IOException("KB JSON is empty");

        List<AbstractMap.SimpleEntry<String, JsonNode>> arrays = new ArrayList<>();
        if (root.isArray()) {
            arrays.add(new AbstractMap.SimpleEntry<>(null, root));
        } else {
            JsonNode recordsNode = root.get("records");
            if (recordsNode == null && root.has("data")) {
                recordsNode = root.get("data");
            }
            if (recordsNode != null && recordsNode.isArray()) {
                arrays.add(new AbstractMap.SimpleEntry<>(null, recordsNode));
            } else {
                root.fields().forEachRemaining(entry -> {
                    if (entry.getValue() != null && entry.getValue().isArray()) {
                        arrays.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
                    }
                });
            }
        }

        if (arrays.isEmpty()) {
            throw new IOException("KB JSON must be an array, or an object with 'records'/'data' array, or section arrays");
        }

        AtomicInteger positionCounter = new AtomicInteger(1);
        List<RagFieldRecord> results = new ArrayList<>();
        for (AbstractMap.SimpleEntry<String, JsonNode> entry : arrays) {
            String sectionOverride = entry.getKey();
            JsonNode arr = entry.getValue();
            arr.forEach(node -> {
                RagFieldRecord rec = mapToRecord(node, sectionOverride, positionCounter.getAndIncrement());
                results.add(rec);
            });
        }
        return results;
    }

    private RagFieldRecord mapToRecord(JsonNode node, String sectionOverride, int fallbackPosition) {
        if (node == null) throw new IllegalArgumentException("Record node is null");

        String rdg = firstText(node, "rdg", "RDG", "rdgName");
        String section = sectionOverride != null ? sectionOverride : firstText(node, "section", "Section", "sec");
        String excelHeader = firstText(node, "excel_header", "excelHeader", "header", "field", "name", "Name");
        String datatype = firstText(node, "datatype", "data_type", "DataType", "Type");
        String format = firstText(node, "format", "rule", "Rule");
        String description = firstText(node, "description", "desc", "Description");
        String dummyValue = firstText(node, "dummy_value", "dummyValue", "example", "Example", "ExampleValue");

        boolean mandatory = boolOr(node, false, "mandatory", "isMandatory", "required", "Required");
        int position = node.has("position") ? node.get("position").asInt(fallbackPosition) : fallbackPosition;

        String normalizedSection = normalizeSection(section, excelHeader);

        return new RagFieldRecord(
                safe(rdg),
                normalizedSection,
                position,
                safe(excelHeader),
                mandatory,
                safe(datatype),
                safe(format),
                safe(description),
                safe(dummyValue)
        );
    }

    private String normalizeSection(String section, String excelHeader) {
        String candidate = safe(section);
        if (candidate.isEmpty()) {
            candidate = inferSectionFromHeader(excelHeader);
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("clinical")) return "ClinicalStaff";
        if (lower.startsWith("section 1")) return "1";
        if (lower.startsWith("section 2")) return "2";
        if (lower.startsWith("section 3")) return "3";
        if (lower.startsWith("section 4")) return "4";
        if (lower.matches("^[1234]$")) return candidate;
        if ("unknown".equals(lower)) return "";
        return candidate;
    }

    private String inferSectionFromHeader(String excelHeader) {
        String header = safe(excelHeader).toLowerCase(Locale.ROOT);
        if (header.contains("section 1")) return "1";
        if (header.contains("section 2")) return "2";
        if (header.contains("section 3")) return "3";
        if (header.contains("section 4")) return "4";
        if (header.contains("clinician") || header.contains("clinical staff")) return "ClinicalStaff";
        return "";
    }

    private boolean boolOr(JsonNode node, boolean defaultVal, String... keys) {
        for (String k : keys) {
            if (node.has(k)) {
                JsonNode v = node.get(k);
                if (v.isBoolean()) return v.asBoolean();
                if (v.isTextual()) {
                    String t = v.asText().trim().toLowerCase(Locale.ROOT);
                    return t.startsWith("y") || t.startsWith("t") || t.equals("1");
                }
                if (v.isNumber()) return v.asInt() != 0;
            }
        }
        return defaultVal;
    }

    private String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k)) {
                JsonNode v = node.get(k);
                if (v != null && !v.isNull()) return v.asText();
            }
        }
        return "";
    }
}
