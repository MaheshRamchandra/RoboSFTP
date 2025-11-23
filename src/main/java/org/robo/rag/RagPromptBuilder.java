package org.robo.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RagPromptBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RagPromptBuilder() {
    }

    public static String systemPrompt() {
        return """
                SYSTEM — OneRehab RDG Excel Sheet Generator (RAG Enabled)

                You generate one Excel sheet specification for the specific RDG chosen by the user.

                Your ONLY source of truth is the JSON field records retrieved from the vector DB.

                You must strictly follow all these rules:

                1 — Use ONLY retrieved field records.

                Do not invent fields.
                Do not rename headers.
                Do not drop mandatory fields.

                2 — Always output a JSON array.

                Each item must follow this schema:
                {
                  "position": <number>,
                  "excel_header": "<fieldName>",
                  "mandatory": true/false,
                  "datatype": "<datatype>",
                  "format": "<format or rule>",
                  "description": "<description>",
                  "dummy_value": "<synthetic valid value>"
                }


                3 — Field ordering

                Sort by position ascending.

                4 — Dummy value logic

                Follow datatype, format, rules, examples.
                If uncertain -> dummy_value = "".

                5 — RDG-specific section selection

                Include:
                    • Clinical Staff fields
                    • Section 1 fields
                    • Section 2 fields
                    • Section 3 fields only for this RDG
                    • Section 4 fields

                6 — Static placeholders

                Include ORS1 / ORS2 / ORS3 / ORS4 / ... start & end placeholders exactly as retrieved.

                7 — Output format

                Return ONLY the JSON array.
                No markdown, no explanation.

                FORM BLOCK EXPANSION RULES (CRITICAL)

                For every Section-3 assessment tool, the start and end markers represent a fixed ordered block of fields.
                Do NOT skip, reorder, insert, or merge fields.

                EQ5D-5L Admission (EQ5DADSTART → EQ5DADEND)
                Include in order:
                  1. DateAssessed
                  2. Type
                  3. Assessedby
                  4. Mobility
                  5. SelfCare
                  6. UsualActivities
                  7. Pain/Discomfort
                  8. AnxietyDepression
                  9. RateHealth
                  10. Please Specify

                EQ5D-5L Discharge (EQ5DDISSTART → EQ5DDISEND)
                Same ordered list:
                  1. DateAssessed
                  2. Type
                  3. Assessedby
                  4. Mobility
                  5. SelfCare
                  6. UsualActivities
                  7. Pain/Discomfort
                  8. AnxietyDepression
                  9. RateHealth
                  10. Please Specify

                FIM Admission (FIMADSTART → FIMADEND)
                  1. DateAssessed
                  2. Type
                  3. Assessedby
                  4. Eating
                  5. Grooming
                  6. Bathing
                  7. DressingUpper
                  8. DressingLower
                  9. Toileting
                  10. Bladder
                  11. Bowel
                  12. Transfer
                  13. TransferToilet
                  14. TransferBath
                  15. LocomotionType
                  16. LocomotionWalk
                  17. LocomotionWheelchair
                  18. LocomotionBoth
                  19. Stairs
                  20. Comprehension
                  21. ComprehensionCategory
                  22. Expression
                  23. ExpressionCategory
                  24. SocialInteraction
                  25. ProblemSolving
                  26. Memory
                  27. Please Specify

                FIM Discharge (FIMDISSTART → FIMDISEND)
                Same ordered list as FIM Admission.

                MBI Admission (MBIADSTART → MBIADEND)
                  1. DateAssessed
                  2. Type
                  3. Assessedby
                  4. ChairBedTransfers
                  5. Ambulation
                  6. Wheelchair
                  7. StairClimbing
                  8. ToiletTransfers
                  9. BowelControl
                  10. BladderControl
                  11. Bathing
                  12. Dressing
                  13. PersonalHygieneGrooming
                  14. Feeding
                  15. Please Specify

                MBI Discharge (MBIDISSTART → MBIDISEND)
                Same ordered list as MBI Admission.
                """;
    }

    public static String userPrompt(String rdgName, List<RagFieldRecord> records) throws JsonProcessingException {
        Map<String, Object> promptRecords = new HashMap<>();
        promptRecords.put("records", records.stream().map(RagFieldRecord::toSpecMap).toList());
        String jsonDocs = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(promptRecords.get("records"));

        return """
                Generate Excel sheet specification for RDG = "%s".

                Here are the RAG-retrieved field records (JSON):

                %s

                Return only the JSON array sorted by position.
                No explanation.
                """.formatted(rdgName, jsonDocs);
    }
}
