package org.robo.rag;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RagScenarioGenerator {
    private static final List<String> PRIORITY_HEADERS = List.of("CreatedBy", "CreatedDate", "ExternalId", "CenterCode");
    private static final Map<String, String> PRIORITY_VALUES = Map.of(
            "CreatedBy", "healthgrp\\ramac0600R",
            "CreatedDate", "2023-07-27",
            "ExternalId", "3AF815BF-307A-4CD8-960A-408DE498677A",
            "CenterCode", "KTPH_Inpatient"
    );
    private static final List<String> FIM_MARKERS = List.of(
            "FIM admission form start", "FIM admission form end",
            "FIM discharge form start", "FIM discharge form end"
    );
    private static final List<String> MBI_MARKERS = List.of(
            "MBI admission form start", "MBI admission form end",
            "MBI discharge form start", "MBI discharge form end"
    );
    private static final List<String> RDG_PREFIXES = List.of(
            "stroke",
            "spinal cord injury",
            "hip fracture",
            "amputation",
            "msk",
            "deconditioning"
    );
    private static final Pattern RANGE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*to\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENUM_PATTERN = Pattern.compile("Allowed values:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private RagScenarioGenerator() {
    }

    public static List<RagFieldRecord> ordered(List<RagFieldRecord> records) {
        Objects.requireNonNull(records, "records");
        List<RagFieldRecord> result = new ArrayList<>();
        // Add priority headers first if present
        for (String h : PRIORITY_HEADERS) {
            for (RagFieldRecord r : records) {
                if (h.equals(r.getExcelHeader())) {
                    result.add(r);
                    break;
                }
            }
        }
        // Add remaining in original order, skipping duplicates
        for (RagFieldRecord r : records) {
            if (result.contains(r)) continue;
            result.add(r);
        }
        return result;
    }

    public static List<RagFieldRecord> filterByAssessmentTool(List<RagFieldRecord> records, String tool) {
        if (records == null) return List.of();
        String selected = tool == null ? "FIM" : tool.trim().toUpperCase(Locale.ROOT);
        boolean useFIM = !"MBI".equals(selected);
        List<String> dropMarkers = useFIM ? MBI_MARKERS : FIM_MARKERS;

        List<RagFieldRecord> filtered = new ArrayList<>();
        boolean skipping = false;
        for (RagFieldRecord r : records) {
            String name = safe(r.getExcelHeader());
            if (dropMarkers.contains(name)) {
                // Start skipping at start marker, stop skipping at corresponding end
                if (!skipping && name.toLowerCase(Locale.ROOT).contains("start")) {
                    skipping = true;
                } else if (skipping && name.toLowerCase(Locale.ROOT).contains("end")) {
                    skipping = false;
                }
                continue;
            }
            if (skipping) continue;
            filtered.add(r);
        }
        return filtered;
    }

    public static String detectAssessmentTool(List<String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        boolean hasFim = containsMarker(headers, FIM_MARKERS);
        boolean hasMbi = containsMarker(headers, MBI_MARKERS);
        if (hasFim && hasMbi) return "MIX";
        if (hasFim) return "FIM";
        if (hasMbi) return "MBI";
        return "";
    }

    private static boolean containsMarker(List<String> headers, List<String> markers) {
        for (String h : headers) {
            String name = safe(h).toLowerCase(Locale.ROOT);
            for (String m : markers) {
                if (name.equals(safe(m).toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> validateAssessmentMarkers(List<RagFieldRecord> records, String tool) {
        if (records == null || records.isEmpty()) return List.of();
        String selected = tool == null ? "FIM" : tool.trim().toUpperCase(Locale.ROOT);
        List<String> markers = "MBI".equals(selected) ? MBI_MARKERS : FIM_MARKERS;

        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            String h = safe(records.get(i).getExcelHeader());
            indexMap.putIfAbsent(h, i);
        }

        List<String[]> pairs = List.of(
                new String[]{markers.get(0), markers.get(1)},
                new String[]{markers.get(2), markers.get(3)}
        );

        List<String> warnings = new ArrayList<>();
        for (String[] pair : pairs) {
            String start = pair[0];
            String end = pair[1];
            boolean hasStart = indexMap.containsKey(start);
            boolean hasEnd = indexMap.containsKey(end);
            if (hasStart ^ hasEnd) {
                warnings.add("Marker mismatch for " + selected + ": found '" + (hasStart ? start : end) + "' without its pair.");
            } else if (hasStart && hasEnd) {
                int startIdx = indexMap.get(start);
                int endIdx = indexMap.get(end);
                if (startIdx > endIdx) {
                    warnings.add("Marker order issue for " + selected + ": '" + start + "' appears after '" + end + "'.");
                }
            }
        }
        return warnings;
    }

    public static List<RagFieldRecord> filterByRdgBlocks(List<RagFieldRecord> records, String rdg) {
        if (records == null) return List.of();
        String selected = rdg == null ? "" : rdg.trim().toLowerCase(Locale.ROOT);
        String keepPrefix = detectRdgKey(selected);
        if (keepPrefix.isEmpty()) return records;

        List<RagFieldRecord> filtered = new ArrayList<>();
        boolean skipping = false;
        for (RagFieldRecord r : records) {
            String nameLower = safe(r.getExcelHeader()).toLowerCase(Locale.ROOT);
            String blockKey = blockPrefix(nameLower);
            if (skipping) {
                if (blockKey != null && !blockKey.equals(keepPrefix) && nameLower.contains("end")) {
                    skipping = false;
                }
                continue;
            }
            if (blockKey != null && !blockKey.equals(keepPrefix)) {
                if (nameLower.contains("start")) {
                    skipping = true;
                }
                continue;
            }
            filtered.add(r);
        }
        return filtered;
    }

    private static String detectRdgKey(String rdgNameLower) {
        String name = safe(rdgNameLower);
        if (name.contains("stroke") || name.startsWith("1")) return "stroke";
        if (name.contains("sci") || name.contains("spinal")) return "spinal cord injury";
        if (name.contains("hip")) return "hip fracture";
        if (name.contains("amputation") || name.contains("amp")) return "amputation";
        if (name.contains("msk")) return "msk";
        if (name.contains("decon") || name.contains("deconditioning")) return "deconditioning";
        return "";
    }

    private static String blockPrefix(String nameLower) {
        for (String p : RDG_PREFIXES) {
            if (nameLower.startsWith(p)) return p;
            if (nameLower.contains(p)) return p;
        }
        return null;
    }

    public static List<String> buildHeaders(List<RagFieldRecord> ordered) {
        List<String> headers = new ArrayList<>();
        headers.add("Name");
        for (RagFieldRecord r : ordered) {
            boolean conditional = parseCondition(r.getFormat()) != null;
            String header = (!conditional && r.isMandatory()) ? "M##" + r.getExcelHeader() : r.getExcelHeader();
            headers.add(header);
        }
        return headers;
    }

    public static List<List<String>> generateScenarios(List<RagFieldRecord> ordered, int count) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            List<String> row = new ArrayList<>();
            Map<String, Integer> headerIndex = new HashMap<>();
            row.add("Scenario" + i);
            headerIndex.put("Name", 0);
            Map<String, String> rowMap = new HashMap<>();
            for (RagFieldRecord r : ordered) {
                boolean conditional = parseCondition(r.getFormat()) != null;
                String header = (!conditional && r.isMandatory()) ? "M##" + r.getExcelHeader() : r.getExcelHeader();
                String val = generateValue(r, rowMap, row, headerIndex);
                row.add(val);
                int idx = row.size() - 1;
                headerIndex.put(header, idx);
                headerIndex.put(r.getExcelHeader(), idx);
                headerIndex.put("M##" + r.getExcelHeader(), idx);
                rowMap.put(r.getExcelHeader(), val);
                rowMap.put("M##" + r.getExcelHeader(), val);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String generateValue(RagFieldRecord rec, Map<String, String> rowMap,
                                       List<String> row, Map<String, Integer> headerIndex) {
        if (!rec.isMandatory()) return "";
        String example = safe(rec.getDummyValue());
        String format = safe(rec.getFormat());
        String datatype = safe(rec.getDatatype());

        Condition condition = parseCondition(format);
        if (condition != null) {
            // Only generate when condition is satisfied; otherwise leave blank
            if (!condition.evaluate(rowMap)) {
                return "";
            }
        }

        if (PRIORITY_VALUES.containsKey(rec.getExcelHeader())) {
            return PRIORITY_VALUES.get(rec.getExcelHeader());
        }

        // Static placeholders: echo header for ORS markers
        if (format.toLowerCase(Locale.ROOT).contains("placeholder") || rec.getExcelHeader().toLowerCase(Locale.ROOT).startsWith("ors")) {
            if (!example.isEmpty()) return example;
            return rec.getExcelHeader();
        }

        // If example is present and non-empty, use it (normalize dates to yyyy-MM-dd)
        if (!example.isEmpty()) {
            if (isDateField(datatype, format, rec.getExcelHeader())) {
                String normalized = normalizeDate(example);
                if (!normalized.isEmpty()) return normalized;
            }
            return example;
        }

        // Enum list parsing
        Optional<String> enumPick = pickFromEnum(format);
        if (enumPick.isPresent()) return enumPick.get();

        // Range parsing
        Optional<String> rangePick = pickFromRange(format);
        if (rangePick.isPresent()) return rangePick.get();

        // Dates
        if (isDateField(datatype, format, rec.getExcelHeader())) {
            return randomDate();
        }

        // Boolean
        if (datatype.toLowerCase(Locale.ROOT).contains("bool")) {
            return RANDOM.nextBoolean() ? "True" : "False";
        }

        // Generic number
        if (datatype.toLowerCase(Locale.ROOT).contains("int") || datatype.toLowerCase(Locale.ROOT).contains("num")) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(1, 9999));
        }

        // Fallback string token
        return rec.getExcelHeader() + "_val";
    }

    private static boolean isDateField(String datatype, String format, String header) {
        String d = safe(datatype).toLowerCase(Locale.ROOT);
        String f = safe(format).toLowerCase(Locale.ROOT);
        String h = safe(header).toLowerCase(Locale.ROOT);
        return d.contains("date") || f.contains("yyyy") || h.contains("date");
    }

    private static String normalizeDate(String value) {
        String v = safe(value);
        if (v.isEmpty()) return v;
        String[] patterns = {
                "uuuu-MM-dd",
                "yyyy-MM-dd",
                "dd/MM/uuuu",
                "d/M/uuuu",
                "dd-MM-uuuu",
                "d-M-uuuu",
                "MM/dd/uuuu",
                "M/d/uuuu"
        };
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p);
                LocalDate parsed = LocalDate.parse(v, fmt);
                return parsed.format(DATE_FMT);
            } catch (DateTimeParseException ignored) {
            }
        }
        // If unable to parse, return original to avoid empty cell
        return v;
    }

    private static int priorityIndex(RagFieldRecord r) {
        int idx = PRIORITY_HEADERS.indexOf(r.getExcelHeader());
        if (idx >= 0) return idx - PRIORITY_HEADERS.size(); // ensure priority headers come first
        return 100;
    }

    private static Optional<String> pickFromEnum(String format) {
        Matcher m = ENUM_PATTERN.matcher(format);
        if (m.find()) {
            String vals = m.group(1);
            String[] parts = vals.split("[,\\n]");
            List<String> cleaned = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) cleaned.add(t);
            }
            if (!cleaned.isEmpty()) {
                return Optional.of(cleaned.get(RANDOM.nextInt(cleaned.size())));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> pickFromRange(String format) {
        Matcher m = RANGE_PATTERN.matcher(format);
        if (m.find()) {
            double min = Double.parseDouble(m.group(1));
            double max = Double.parseDouble(m.group(2));
            if (min > max) {
                double tmp = min;
                min = max;
                max = tmp;
            }
            int pick = (int) Math.round(min + RANDOM.nextDouble() * (max - min));
            return Optional.of(String.valueOf(pick));
        }
        return Optional.empty();
    }

    private static String randomDate() {
        LocalDate start = LocalDate.of(1970, 1, 1);
        long days = ThreadLocalRandom.current().nextLong(0, 365 * 60L);
        return start.plusDays(days).format(DATE_FMT);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    public static Condition parseCondition(String rule) {
        if (rule == null) return null;
        String lower = rule.toLowerCase(Locale.ROOT);
        if (!lower.contains("only if")) return null;
        String after = rule.substring(lower.indexOf("only if") + "only if".length()).trim();

        String field = null;
        String rest = after;
        boolean negate = false;
        for (String sep : new String[]{" is either ", " is not ", " is ", " = ", " equals "}) {
            int idx = after.toLowerCase(Locale.ROOT).indexOf(sep);
            if (idx >= 0) {
                field = after.substring(0, idx).replaceAll("[\\[\\]\\(\\)]", "").trim();
                rest = after.substring(idx + sep.length()).trim();
                if (sep.contains("not")) negate = true;
                break;
            }
        }
        if (field == null || field.isEmpty()) return null;

        List<String> vals = new ArrayList<>();
        Matcher m = Pattern.compile("[\"'“”‘’]?([A-Za-z0-9_./+-]+)[\"'“”‘’]?").matcher(rest);
        while (m.find()) {
            String v = m.group(1).trim();
            if (!v.isEmpty() && !v.equalsIgnoreCase(field)) vals.add(v);
        }
        if (vals.isEmpty()) return null;
        return new Condition(field, vals, negate);
    }

    private static final class Condition {
        private final String field;
        private final List<String> values;
        private final boolean negate;

        Condition(String field, List<String> values, boolean negate) {
            this.field = field;
            this.values = values;
            this.negate = negate;
        }

        boolean evaluate(Map<String, String> rowMap) {
            if (rowMap == null) return false;
            String actual = getCaseInsensitive(rowMap, field);
            if (actual == null || actual.isEmpty()) return false;
            boolean inSet = values.stream().anyMatch(v -> actual.equalsIgnoreCase(v));
            return negate ? !inSet : inSet;
        }

        private String getCaseInsensitive(Map<String, String> map, String key) {
            if (map.containsKey(key)) return safe(map.get(key));
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    return safe(e.getValue());
                }
            }
            return null;
        }
    }
}
