package org.robo.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagScenarioGeneratorTest {

    @Test
    void filterByAssessmentTool_removesOtherToolSections() {
        List<RagFieldRecord> records = List.of(
                rec("FIM admission form start"),
                rec("FIM Field"),
                rec("FIM admission form end"),
                rec("MBI admission form start"),
                rec("MBI Field"),
                rec("MBI admission form end")
        );

        List<RagFieldRecord> fimOnly = RagScenarioGenerator.filterByAssessmentTool(records, "FIM");
        assertTrue(fimOnly.stream().anyMatch(r -> r.getExcelHeader().equals("FIM Field")));
        assertFalse(fimOnly.stream().anyMatch(r -> r.getExcelHeader().equals("MBI Field")));

        List<RagFieldRecord> mbiOnly = RagScenarioGenerator.filterByAssessmentTool(records, "MBI");
        assertTrue(mbiOnly.stream().anyMatch(r -> r.getExcelHeader().equals("MBI Field")));
        assertFalse(mbiOnly.stream().anyMatch(r -> r.getExcelHeader().equals("FIM Field")));
    }

    @Test
    void validateAssessmentMarkers_flagsMissingPair() {
        List<RagFieldRecord> records = List.of(
                rec("FIM admission form start"),
                rec("FIM admission form end"),
                rec("FIM discharge form start") // missing end
        );

        List<String> warnings = RagScenarioGenerator.validateAssessmentMarkers(records, "FIM");
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).toLowerCase().contains("discharge"));
    }

    @Test
    void detectAssessmentTool_identifiesMixedHeaders() {
        assertEquals("FIM", RagScenarioGenerator.detectAssessmentTool(List.of("FIM admission form start")));
        assertEquals("MBI", RagScenarioGenerator.detectAssessmentTool(List.of("MBI discharge form end")));
        assertEquals("MIX", RagScenarioGenerator.detectAssessmentTool(
                List.of("FIM admission form start", "MBI admission form start")));
        assertEquals("", RagScenarioGenerator.detectAssessmentTool(List.of("Other header")));
    }

    private static RagFieldRecord rec(String header) {
        return new RagFieldRecord("", "", 0, header, true, "", "", "", "");
    }
}
