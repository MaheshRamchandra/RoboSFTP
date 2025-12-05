package org.robo.rag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates a pipe-delimited string against KB metadata.
 */
public final class RagStringValidator {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final Pattern RANGE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*to\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALLOWED_VALUES_PATTERN = Pattern.compile("Allowed values:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private RagStringValidator() {
    }

    public static ValidationResult validate(List<String> tokens, List<RagFieldRecord> orderedFields) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RagFieldRecord> fields = orderedFields == null ? List.of() : orderedFields;

        if (tokens == null) tokens = List.of();
        if (tokens.size() > fields.size()) {
            errors.add("Input has " + tokens.size() + " values but KB defines only " + fields.size() + " fields.");
        }

        int fieldCount = Math.min(tokens.size(), fields.size());
        for (int i = 0; i < fieldCount; i++) {
            RagFieldRecord field = fields.get(i);
            String value = tokens.get(i);
            validateField(field, value, i, errors, warnings);
        }

        for (int i = fieldCount; i < fields.size(); i++) {
            RagFieldRecord field = fields.get(i);
            if (field.isMandatory()) {
                errors.add("Missing value for mandatory field '" + field.getExcelHeader() + "' (position " + (i + 1) + ").");
            }
        }

        return new ValidationResult(errors, warnings);
    }

    private static void validateField(RagFieldRecord field, String value, int index,
                                      List<String> errors, List<String> warnings) {
        String name = safe(field.getExcelHeader());
        String rule = safe(field.getFormat());
        String datatype = safe(field.getDatatype());
        String trimmed = value == null ? "" : value.trim();

        if (trimmed.isEmpty()) {
            if (field.isMandatory()) {
                errors.add("Mandatory field '" + name + "' at position " + (index + 1) + " is empty.");
            }
            return;
        }

        // Allowed values
        Set<String> allowed = parseAllowedValues(rule);
        if (!allowed.isEmpty() && !allowed.contains(trimmed)) {
            errors.add("Field '" + name + "' value '" + trimmed + "' not in allowed values: " + allowed);
        }

        // Range
        double[] range = parseRange(rule);
        if (range != null) {
            try {
                double v = Double.parseDouble(trimmed);
                if (v < range[0] || v > range[1]) {
                    errors.add("Field '" + name + "' value '" + trimmed + "' outside range " + range[0] + " to " + range[1] + ".");
                }
            } catch (NumberFormatException ex) {
                errors.add("Field '" + name + "' expects numeric range but got '" + trimmed + "'.");
            }
        }

        // Date format
        if (rule.toLowerCase(Locale.ROOT).contains("yyyy-mm-dd") || datatype.toLowerCase(Locale.ROOT).contains("date")) {
            try {
                LocalDate.parse(trimmed, DATE_FMT);
            } catch (DateTimeParseException ex) {
                errors.add("Field '" + name + "' expects date yyyy-MM-dd but got '" + trimmed + "'.");
            }
        }

        // Numeric check for enum/number/integer
        String lowerType = datatype.toLowerCase(Locale.ROOT);
        if ((lowerType.contains("enum") || lowerType.contains("int") || lowerType.contains("num")) && allowed.isEmpty() && range == null) {
            if (!trimmed.matches("-?\\d+(\\.\\d+)?")) {
                warnings.add("Field '" + name + "' expected numeric-like value but got '" + trimmed + "'.");
            }
        }
    }

    private static Set<String> parseAllowedValues(String rule) {
        Set<String> values = new HashSet<>();
        Matcher m = ALLOWED_VALUES_PATTERN.matcher(rule);
        if (m.find()) {
            String body = m.group(1);
            String[] parts = body.split("[,\n]");
            for (String p : parts) {
                String v = p.replaceAll("//.*", "").trim();
                if (!v.isEmpty()) values.add(v);
            }
        }
        return values;
    }

    private static double[] parseRange(String rule) {
        Matcher m = RANGE_PATTERN.matcher(rule);
        if (m.find()) {
            try {
                double min = Double.parseDouble(m.group(1));
                double max = Double.parseDouble(m.group(2));
                if (min > max) {
                    double tmp = min;
                    min = max;
                    max = tmp;
                }
                return new double[]{min, max};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    public record ValidationResult(List<String> errors, List<String> warnings) {
    }
}
