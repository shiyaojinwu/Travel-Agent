package com.sz.aiagent.conversation.api;

import com.sz.aiagent.conversation.domain.ApiFailure;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses = ConversationController.class)
public class ApiErrors {
  @ExceptionHandler(ApiFailure.class)
  public ResponseEntity<?> failure(ApiFailure e) {
    return ResponseEntity.status(e.status)
        .body(
            Map.of(
                "code",
                e.code,
                "message",
                e.getMessage(),
                "retryable",
                e.status == 429 || e.status >= 500,
                "traceId",
                UUID.randomUUID().toString()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> input(Exception e) {
    return failure(new ApiFailure(400, "INVALID_INPUT", "请求格式不正确。"));
  }
}
