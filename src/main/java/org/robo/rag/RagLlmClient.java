package org.robo.rag;

public interface RagLlmClient {
    String generate(String systemPrompt, String userPrompt) throws Exception;
}
