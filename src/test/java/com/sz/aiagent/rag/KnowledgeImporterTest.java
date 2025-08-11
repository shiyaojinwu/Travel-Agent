package com.sz.aiagent.rag;

import static org.assertj.core.api.Assertions.*;

import com.sz.aiagent.rag.etl.extract.TravelAppDocumentExtract;
import com.sz.aiagent.rag.etl.load.KnowledgeImporter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeImporterTest {
  private Document doc(String text) {
    return new Document(text, Map.of("filename", "深圳.md"));
  }

  @Test
  void sameSourceKeepsStableIdsAndHashes() {
    var first = KnowledgeImporter.prepare(List.of(doc("景点")), "v1");
    var second = KnowledgeImporter.prepare(List.of(doc("景点")), "v1");
    assertThat(first).isEqualTo(second);
    assertThat(first.getFirst().getMetadata())
        .containsEntry("source", "document/深圳.md")
        .containsEntry("dataset", "travel-markdown")
        .containsEntry("version", "v1");
  }

  @Test
  void editChangesHashButUpsertsSameChunkIdentity() {
    var first = KnowledgeImporter.prepare(List.of(doc("旧内容")), "v1").getFirst();
    var second = KnowledgeImporter.prepare(List.of(doc("新内容")), "v1").getFirst();
    assertThat(first.getId()).isEqualTo(second.getId());
    assertThat(first.getMetadata().get("contentHash"))
        .isNotEqualTo(second.getMetadata().get("contentHash"));
  }

  @Test
  void differentVersionsAndChunkPositionsNeverShareIds() {
    var one = KnowledgeImporter.prepare(List.of(doc("相同"), doc("相同")), "v1");
    var two = KnowledgeImporter.prepare(List.of(doc("相同")), "v2");
    assertThat(List.of(one.get(0).getId(), one.get(1).getId(), two.get(0).getId()))
        .doesNotHaveDuplicates();
  }

  @Test
  void bundledKnowledgeIsReadableWithoutCallingModels() {
    var extract = new TravelAppDocumentExtract(new PathMatchingResourcePatternResolver());
    var docs = extract.loadMarkdowns();
    assertThat(docs)
        .isNotEmpty()
        .allSatisfy(
            doc -> {
              assertThat(doc.getText()).isNotBlank();
              assertThat(doc.getMetadata()).containsKey("filename");
            });
  }

  @Test
  void repeatImportSkipsEmbeddingsAndOnlyDeletesRemovedManagedChunks() throws Exception {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var store = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
    var extract = org.mockito.Mockito.mock(TravelAppDocumentExtract.class);
    var source = List.of(doc("原文"));
    org.mockito.Mockito.when(extract.loadMarkdowns()).thenReturn(source);
    var prepared = KnowledgeImporter.prepare(source, "v1").getFirst();
    var rows =
        List.of(
            new String[] {prepared.getId(), (String) prepared.getMetadata().get("contentHash")},
            new String[] {"stale-id", "old-hash"});
    stubExisting(jdbc, rows);
    assertThat(new KnowledgeImporter(jdbc, store, extract, "v1").importKnowledge()).isZero();
    org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
        .add(org.mockito.ArgumentMatchers.anyList());
    org.mockito.Mockito.verify(store).delete(List.of("stale-id"));
  }

  @Test
  void changedContentIsUpsertedUsingExistingId() throws Exception {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var store = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
    var extract = org.mockito.Mockito.mock(TravelAppDocumentExtract.class);
    var source = List.of(doc("更新原文"));
    org.mockito.Mockito.when(extract.loadMarkdowns()).thenReturn(source);
    var prepared = KnowledgeImporter.prepare(source, "v1").getFirst();
    stubExisting(
        jdbc, java.util.Collections.singletonList(new String[] {prepared.getId(), "old-hash"}));
    assertThat(new KnowledgeImporter(jdbc, store, extract, "v1").importKnowledge()).isEqualTo(1);
    org.mockito.Mockito.verify(store).add(List.of(prepared));
    org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
        .delete(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void emptySourceCannotDeleteAnExistingKnowledgeBase() {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var store = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
    var extract = org.mockito.Mockito.mock(TravelAppDocumentExtract.class);
    org.mockito.Mockito.when(extract.loadMarkdowns()).thenReturn(List.of());
    assertThatThrownBy(() -> new KnowledgeImporter(jdbc, store, extract, "v1").importKnowledge())
        .isInstanceOf(IllegalStateException.class);
    org.mockito.Mockito.verifyNoInteractions(store, jdbc);
  }

  @SuppressWarnings("unchecked")
  private void stubExisting(org.springframework.jdbc.core.JdbcTemplate jdbc, List<String[]> rows)
      throws Exception {
    org.mockito.Mockito.when(
            jdbc.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(
                    org.springframework.jdbc.core.ResultSetExtractor.class),
                org.mockito.ArgumentMatchers.eq(KnowledgeImporter.DATASET),
                org.mockito.ArgumentMatchers.eq("v1")))
        .thenAnswer(
            invocation -> {
              var rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
              var position = new java.util.concurrent.atomic.AtomicInteger(-1);
              org.mockito.Mockito.when(rs.next())
                  .thenAnswer(call -> position.incrementAndGet() < rows.size());
              org.mockito.Mockito.when(rs.getString(1))
                  .thenAnswer(call -> rows.get(position.get())[0]);
              org.mockito.Mockito.when(rs.getString(2))
                  .thenAnswer(call -> rows.get(position.get())[1]);
              org.springframework.jdbc.core.ResultSetExtractor<?> extractor =
                  invocation.getArgument(1);
              return extractor.extractData(rs);
            });
  }
}
