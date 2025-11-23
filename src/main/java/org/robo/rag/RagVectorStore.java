package org.robo.rag;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface RagVectorStore {
    void refresh(File file) throws IOException;

    List<RagFieldRecord> retrieve(String rdg);

    List<RagFieldRecord> retrieve(String rdg, Set<String> sections);

    boolean isLoaded();

    String sourceDescription();

    Set<String> defaultSections();
}
