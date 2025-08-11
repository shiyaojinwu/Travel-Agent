package com.sz.aiagent.conversation.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.aiagent.conversation.domain.ApiFailure;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ConversationStore {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ObjectMapper json;

  public ConversationStore(
      JdbcTemplate db,
      org.springframework.transaction.PlatformTransactionManager manager,
      ObjectMapper json) {
    this.db = db;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
  }

  public record Conversation(
      String id, String title, String mode, String constraints, OffsetDateTime updatedAt) {}

  public record Message(long seq, String role, String content, String status, String runId) {}

  public record Run(
      String id,
      String conversationId,
      String text,
      String constraints,
      String status,
      String model,
      String promptVersion,
      String knowledgeVersion,
      String toolPolicy,
      String errorCode,
      long lastEvent) {}

  public record Started(Run run, boolean created) {}

  public record Event(long seq, String type, Map<String, Object> data) {}

  public record Summary(int version, long coveredSeq, String content) {}

  private static boolean active(String s) {
    return s.equals("QUEUED") || s.equals("RUNNING");
  }

  public boolean ownerExists(String owner) {
    return db.queryForObject("SELECT COUNT(*) FROM ta_owner WHERE id=?", Integer.class, owner) > 0;
  }

  public void createOwner(String owner) {
    db.update("INSERT INTO ta_owner VALUES (?,?)", owner, OffsetDateTime.now());
  }

  public List<Conversation> list(String owner) {
    return db.query(
        "SELECT * FROM ta_conversation WHERE owner_id=? ORDER BY updated_at DESC LIMIT 100",
        (rs, n) ->
            new Conversation(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("mode"),
                rs.getString("constraints_text"),
                rs.getObject("updated_at", OffsetDateTime.class)),
        owner);
  }

  public Conversation conversation(String owner, String id) {
    return db
        .query(
            "SELECT * FROM ta_conversation WHERE owner_id=? AND id=?",
            (rs, n) ->
                new Conversation(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("mode"),
                    rs.getString("constraints_text"),
                    rs.getObject("updated_at", OffsetDateTime.class)),
            owner,
            id)
        .stream()
        .findFirst()
        .orElseThrow(ApiFailure::missing);
  }

  public Conversation create(String owner, String title, String mode, String constraints) {
    return tx.execute(
        s -> {
          db.queryForObject("SELECT id FROM ta_owner WHERE id=? FOR UPDATE", String.class, owner);
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM ta_conversation WHERE owner_id=?", Integer.class, owner)
              >= 100) throw new ApiFailure(429, "CONVERSATION_LIMIT", "最多保存 100 个旅行会话。");
          String id = UUID.randomUUID().toString();
          var now = OffsetDateTime.now();
          db.update(
              "INSERT INTO ta_conversation VALUES (?,?,?,?,?,?,?)",
              id,
              owner,
              title,
              mode,
              constraints,
              now,
              now);
          return conversation(owner, id);
        });
  }

  public void update(String owner, String id, String title, String constraints) {
    tx.executeWithoutResult(
        s -> {
          conversation(owner, id);
          db.queryForObject(
              "SELECT id FROM ta_conversation WHERE id=? FOR UPDATE", String.class, id);
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM ta_run WHERE conversation_id=? AND status IN"
                      + " ('QUEUED','RUNNING')",
                  Integer.class,
                  id)
              > 0) throw new ApiFailure(409, "RUN_ACTIVE", "请等待生成结束或先停止生成，再修改旅行信息。");
          db.update(
              "UPDATE ta_conversation SET title=?,constraints_text=?,updated_at=? WHERE id=?",
              title,
              constraints,
              OffsetDateTime.now(),
              id);
        });
  }

  private Run mapRun(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Run(
        rs.getString("id"),
        rs.getString("conversation_id"),
        rs.getString("request_text"),
        rs.getString("constraints_text"),
        rs.getString("status"),
        rs.getString("model"),
        rs.getString("prompt_version"),
        rs.getString("knowledge_version"),
        rs.getString("tool_policy"),
        rs.getString("error_code"),
        rs.getLong("next_event"));
  }

  public Run run(String id) {
    return db.query("SELECT * FROM ta_run WHERE id=?", this::mapRun, id).stream()
        .findFirst()
        .orElseThrow(ApiFailure::missing);
  }

  public Run ownedRun(String owner, String id) {
    Run r = run(id);
    conversation(owner, r.conversationId());
    return r;
  }

  public List<Run> runs(String owner, String cid) {
    conversation(owner, cid);
    return db.query(
        "SELECT * FROM ta_run WHERE conversation_id=? ORDER BY created_at", this::mapRun, cid);
  }

  public List<Message> messages(String owner, String cid) {
    conversation(owner, cid);
    return history(cid);
  }

  public List<Message> history(String cid) {
    return db.query(
        "SELECT * FROM ta_message WHERE conversation_id=? ORDER BY seq",
        (rs, n) ->
            new Message(
                rs.getLong("seq"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("run_id")),
        cid);
  }

  public Started start(
      String owner,
      String cid,
      String key,
      String text,
      String model,
      String prompt,
      String knowledge,
      String policy,
      int rate) {
    return tx.execute(
        s -> {
          db.queryForObject("SELECT id FROM ta_owner WHERE id=? FOR UPDATE", String.class, owner);
          Conversation c = conversation(owner, cid);
          db.queryForObject(
              "SELECT id FROM ta_conversation WHERE id=? FOR UPDATE", String.class, cid);
          var previous =
              db.query(
                  "SELECT * FROM ta_run WHERE conversation_id=? AND request_key=?",
                  this::mapRun,
                  cid,
                  key);
          if (!previous.isEmpty()) {
            if (!previous.getFirst().text().equals(text))
              throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "相同请求标识不能用于不同消息。");
            return new Started(previous.getFirst(), false);
          }
          int ongoing =
              db.queryForObject(
                  "SELECT COUNT(*) FROM ta_run r JOIN ta_conversation c ON r.conversation_id=c.id"
                      + " WHERE c.owner_id=? AND r.status IN ('QUEUED','RUNNING')",
                  Integer.class,
                  owner);
          if (ongoing > 0) throw new ApiFailure(409, "RUN_ACTIVE", "当前浏览器已有任务运行，请等待完成或停止后再发送。");
          int recent =
              db.queryForObject(
                  "SELECT COUNT(*) FROM ta_run r JOIN ta_conversation c ON r.conversation_id=c.id"
                      + " WHERE c.owner_id=? AND r.created_at>?",
                  Integer.class,
                  owner,
                  OffsetDateTime.now().minusHours(1));
          if (recent >= rate) throw new ApiFailure(429, "RATE_LIMIT", "已达到每小时请求上限，请稍后再试。");
          String id = UUID.randomUUID().toString();
          var now = OffsetDateTime.now();
          db.update(
              "INSERT INTO"
                  + " ta_run(id,conversation_id,request_key,request_text,constraints_text,status,model,prompt_version,knowledge_version,tool_policy,created_at)"
                  + " VALUES (?,?,?,?,?,'QUEUED',?,?,?,?,?)",
              id,
              cid,
              key,
              text,
              c.constraints(),
              model,
              prompt,
              knowledge,
              policy,
              now);
          db.update(
              "INSERT INTO ta_message(conversation_id,run_id,role,content,status,created_at) VALUES"
                  + " (?,?,'user',?,'SAVED',?)",
              cid,
              id,
              text,
              now);
          db.update("UPDATE ta_conversation SET updated_at=? WHERE id=?", now, cid);
          return new Started(run(id), true);
        });
  }

  public boolean claim(String id) {
    return db.update("UPDATE ta_run SET status='RUNNING' WHERE id=? AND status='QUEUED'", id) == 1;
  }

  public boolean isActive(String id) {
    return active(run(id).status());
  }

  private String encode(Object data) {
    try {
      return json.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> decode(String data) {
    try {
      return json.readValue(data, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private void append(String id, String type, Map<String, Object> data) {
    long seq =
        db.queryForObject("SELECT next_event FROM ta_run WHERE id=? FOR UPDATE", Long.class, id)
            + 1;
    db.update("UPDATE ta_run SET next_event=? WHERE id=?", seq, id);
    db.update(
        "INSERT INTO ta_run_event VALUES (?,?,?,?,?)",
        id,
        seq,
        type,
        encode(data),
        OffsetDateTime.now());
  }

  public void event(String id, String type, Map<String, Object> data) {
    tx.executeWithoutResult(
        s -> {
          db.queryForObject("SELECT id FROM ta_run WHERE id=? FOR UPDATE", String.class, id);
          if (active(run(id).status())) append(id, type, data);
        });
  }

  public List<Event> events(String id, long after) {
    return db.query(
        "SELECT * FROM ta_run_event WHERE run_id=? AND seq>? ORDER BY seq LIMIT 300",
        (rs, n) ->
            new Event(rs.getLong("seq"), rs.getString("type"), decode(rs.getString("payload"))),
        id,
        after);
  }

  public void finish(String id, String state, String error, String content) {
    tx.executeWithoutResult(
        s -> {
          db.queryForObject("SELECT id FROM ta_run WHERE id=? FOR UPDATE", String.class, id);
          Run r = run(id);
          if (!active(r.status())) return;
          String answer = content;
          if (answer == null) {
            var parts =
                db.query(
                    "SELECT payload FROM ta_run_event WHERE run_id=? AND type='delta' ORDER BY seq",
                    (rs, n) -> decode(rs.getString(1)).getOrDefault("content", "").toString(),
                    id);
            answer = String.join("", parts);
          }
          if (!answer.isBlank())
            db.update(
                "INSERT INTO ta_message(conversation_id,run_id,role,content,status,created_at)"
                    + " VALUES (?,?,'assistant',?,?,?)",
                r.conversationId(),
                id,
                answer,
                state,
                OffsetDateTime.now());
          db.update(
              "UPDATE ta_run SET status=?,error_code=?,finished_at=? WHERE id=?",
              state,
              error,
              OffsetDateTime.now(),
              id);
          if (content != null && !content.isBlank())
            append(id, "final", Map.of("content", content));
          append(id, "done", Map.of("state", state, "errorCode", error == null ? "" : error));
          db.update(
              "UPDATE ta_conversation SET updated_at=? WHERE id=?",
              OffsetDateTime.now(),
              r.conversationId());
        });
  }

  public void recover() {
    db.queryForList("SELECT id FROM ta_run WHERE status IN ('QUEUED','RUNNING')", String.class)
        .forEach(id -> finish(id, "INTERRUPTED", "SERVER_RESTART", null));
  }

  public void call(
      String id,
      String purpose,
      int input,
      int output,
      boolean estimated,
      String status,
      long ms,
      Long firstToken) {
    db.update(
        "INSERT INTO ta_model_call VALUES (?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        purpose,
        input,
        output,
        estimated,
        status,
        ms,
        firstToken,
        OffsetDateTime.now());
  }

  public int usedTokens(String id) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(input_tokens+output_tokens),0) FROM ta_model_call WHERE run_id=?",
        Integer.class,
        id);
  }

  public List<Map<String, Object>> usage(String id) {
    return db.queryForList(
        "SELECT purpose,input_tokens,output_tokens,estimated,status,latency_ms,first_token_ms FROM"
            + " ta_model_call WHERE run_id=? ORDER BY created_at",
        id);
  }

  public void tool(String id, String name, String status, long ms) {
    db.update(
        "INSERT INTO ta_tool_execution VALUES (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        name,
        status,
        ms,
        OffsetDateTime.now());
  }

  public List<Map<String, Object>> tools(String id) {
    return db.queryForList(
        "SELECT tool_name,status,latency_ms FROM ta_tool_execution WHERE run_id=? ORDER BY"
            + " created_at",
        id);
  }

  public Summary summary(String cid) {
    return db
        .query(
            "SELECT * FROM ta_summary WHERE conversation_id=?",
            (rs, n) ->
                new Summary(
                    rs.getInt("version"), rs.getLong("covered_seq"), rs.getString("content")),
            cid)
        .stream()
        .findFirst()
        .orElse(new Summary(0, 0, ""));
  }

  public void summary(String cid, long covered, String content) {
    tx.executeWithoutResult(
        s -> {
          db.queryForObject(
              "SELECT id FROM ta_conversation WHERE id=? FOR UPDATE", String.class, cid);
          var old = summary(cid);
          if (covered <= old.coveredSeq()) return;
          if (old.version() == 0)
            db.update(
                "INSERT INTO ta_summary VALUES (?,1,?,?,?)",
                cid,
                covered,
                content,
                OffsetDateTime.now());
          else
            db.update(
                "UPDATE ta_summary SET version=version+1,covered_seq=?,content=?,updated_at=? WHERE"
                    + " conversation_id=?",
                covered,
                content,
                OffsetDateTime.now(),
                cid);
        });
  }
}
