package com.sz.aiagent.conversation.api;

import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class BrowserIdentity {
  private final ConversationStore store;
  private final SecureRandom random = new SecureRandom();

  public BrowserIdentity(ConversationStore store) {
    this.store = store;
  }

  public String resolve(HttpServletRequest request, HttpServletResponse response) {
    String token = null;
    if (request.getCookies() != null)
      for (var cookie : request.getCookies())
        if (cookie.getName().equals("travel_owner") && cookie.getValue().matches("[a-f0-9]{64}"))
          token = cookie.getValue();
    if (token != null && store.ownerExists(hash(token))) return hash(token);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    token = HexFormat.of().formatHex(bytes);
    String owner = hash(token);
    store.createOwner(owner);
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("travel_owner", token)
            .path(request.getContextPath().isEmpty() ? "/" : request.getContextPath())
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .maxAge(java.time.Duration.ofDays(180))
            .build()
            .toString());
    return owner;
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
