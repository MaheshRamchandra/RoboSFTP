package org.robo.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical representation of a field record used by the RAG flow.
 * Fields are aligned with the JSON schema expected by the LLM.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagFieldRecord {

    @JsonProperty("rdg")
    private String rdg;

    @JsonProperty("section")
    private String section;

    @JsonProperty("position")
    private int position;

    @JsonProperty("excel_header")
    private String excelHeader;

    @JsonProperty("mandatory")
    private boolean mandatory;

    @JsonProperty("datatype")
    private String datatype;

    @JsonProperty("format")
    private String format;

    @JsonProperty("description")
    private String description;

    @JsonProperty("dummy_value")
    private String dummyValue;

    public RagFieldRecord() {
        // for Jackson
    }

    public RagFieldRecord(String rdg,
                          String section,
                          int position,
                          String excelHeader,
                          boolean mandatory,
                          String datatype,
                          String format,
                          String description,
                          String dummyValue) {
        this.rdg = rdg;
        this.section = section;
        this.position = position;
        this.excelHeader = excelHeader;
        this.mandatory = mandatory;
        this.datatype = datatype;
        this.format = format;
        this.description = description;
        this.dummyValue = dummyValue;
    }

    public String getRdg() {
        return rdg;
    }

    public String getSection() {
        return section;
    }

    public int getPosition() {
        return position;
    }

    public String getExcelHeader() {
        return excelHeader;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getDatatype() {
        return datatype;
    }

    public String getFormat() {
        return format;
    }

    public String getDescription() {
        return description;
    }

    public String getDummyValue() {
        return dummyValue;
    }

    public Map<String, Object> toSpecMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("position", position);
        m.put("excel_header", nullSafe(excelHeader));
        m.put("mandatory", mandatory);
        m.put("datatype", nullSafe(datatype));
        m.put("format", nullSafe(format));
        m.put("description", nullSafe(description));
        m.put("dummy_value", nullSafe(dummyValue));
        return m;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "RagFieldRecord{" +
                "rdg='" + rdg + '\'' +
                ", section='" + section + '\'' +
                ", position=" + position +
                ", excelHeader='" + excelHeader + '\'' +
                ", mandatory=" + mandatory +
                ", datatype='" + datatype + '\'' +
                ", format='" + format + '\'' +
                ", description='" + description + '\'' +
                ", dummyValue='" + dummyValue + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RagFieldRecord)) return false;
        RagFieldRecord that = (RagFieldRecord) o;
        return position == that.position &&
                mandatory == that.mandatory &&
                Objects.equals(rdg, that.rdg) &&
                Objects.equals(section, that.section) &&
                Objects.equals(excelHeader, that.excelHeader) &&
                Objects.equals(datatype, that.datatype) &&
                Objects.equals(format, that.format) &&
                Objects.equals(description, that.description) &&
                Objects.equals(dummyValue, that.dummyValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rdg, section, position, excelHeader, mandatory, datatype, format, description, dummyValue);
    }
}
