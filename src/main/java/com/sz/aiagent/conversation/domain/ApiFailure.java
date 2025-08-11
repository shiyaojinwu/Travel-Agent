package com.sz.aiagent.conversation.domain;

public class ApiFailure extends RuntimeException {
  public final int status;
  public final String code;

  public ApiFailure(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiFailure missing() {
    return new ApiFailure(404, "NOT_FOUND", "记录不存在或不属于当前浏览器。");
  }
}
