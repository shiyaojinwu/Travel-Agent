package com.sz.aiagent.rag.etl.load;

import com.sz.aiagent.rag.etl.extract.TravelAppDocumentExtract;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles only this application's dataset and version; other collections stay untouched. */
@Service
public class KnowledgeImporter {
  public static final String DATASET = "travel-markdown";
  private final JdbcTemplate jdbc;
  private final VectorStore store;
  private final TravelAppDocumentExtract extract;
  private final String version;

  public KnowledgeImporter(
      JdbcTemplate jdbc,
      VectorStore store,
      TravelAppDocumentExtract extract,
      @Value("${travel.rag.version:v1}") String version) {
    this.jdbc = jdbc;
    this.store = store;
    this.extract = extract;
    this.version = version;
  }

  public static List<Document> prepare(List<Document> input, String version) {
    Map<String, Integer> positions = new HashMap<>();
    List<Document> chunks = new ArrayList<>();
    for (Document document : input) {
      String filename = Objects.toString(document.getMetadata().get("filename"), "unknown");
      int index = positions.merge(filename, 1, Integer::sum) - 1;
      String id =
          UUID.nameUUIDFromBytes(
                  (DATASET + ":" + version + ":" + filename + ":" + index)
                      .getBytes(StandardCharsets.UTF_8))
              .toString();
      Map<String, Object> metadata = new HashMap<>(document.getMetadata());
      metadata.put("dataset", DATASET);
      metadata.put("version", version);
      metadata.put("chunk", index);
      metadata.put("source", "document/" + filename);
      try {
        metadata.put(
            "contentHash",
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(document.getText().getBytes(StandardCharsets.UTF_8))));
      } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
      chunks.add(new Document(id, document.getText(), metadata));
    }
    return chunks;
  }

  @Transactional
  public int importKnowledge() {
    List<Document> documents = prepare(extract.loadMarkdowns(), version);
    if (documents.isEmpty()) throw new IllegalStateException("没有读取到知识文档，取消导入以保留现有数据");
    jdbc.execute("SELECT pg_advisory_xact_lock(735812419)");
    Map<String, String> existing = new HashMap<>();
    jdbc.query(
        "SELECT id::text, metadata->>'contentHash' FROM vector_store "
            + "WHERE metadata->>'dataset' = ? AND metadata->>'version' = ?",
        rs -> {
          while (rs.next()) existing.put(rs.getString(1), rs.getString(2));
          return null;
        },
        DATASET,
        version);
    List<Document> changed =
        documents.stream()
            .filter(
                d -> !Objects.equals(existing.get(d.getId()), d.getMetadata().get("contentHash")))
            .toList();
    Set<String> expected = new HashSet<>();
    documents.forEach(d -> expected.add(d.getId()));
    List<String> removed = existing.keySet().stream().filter(id -> !expected.contains(id)).toList();
    if (!changed.isEmpty()) store.add(changed); // PGVector upserts stable UUIDs.
    if (!removed.isEmpty()) store.delete(removed);
    return changed.size();
  }
}
