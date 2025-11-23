package org.robo.rag;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class RagService {
    private final RagVectorStore vectorStore;

    public RagService(RagVectorStore vectorStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore is required");
    }

    public List<RagFieldRecord> retrieve(String rdg) {
        return vectorStore.retrieve(rdg);
    }

    public List<RagFieldRecord> retrieve(String rdg, Set<String> sections) {
        return vectorStore.retrieve(rdg, sections);
    }

    public String buildUserPrompt(String rdg, List<RagFieldRecord> records) throws JsonProcessingException {
        return RagPromptBuilder.userPrompt(rdg, records);
    }

    public String buildSystemPrompt() {
        return RagPromptBuilder.systemPrompt();
    }

    public String callLlm(RagLlmClient client, String rdg, List<RagFieldRecord> records) throws Exception {
        String system = buildSystemPrompt();
        String user = buildUserPrompt(rdg, records);
        return client.generate(system, user);
    }

    public String offlineSpec(List<RagFieldRecord> records) throws JsonProcessingException {
        return RagSpecBuilder.toJsonArray(records);
    }
}
