package com.sz.aiagent.agent;

import com.sz.aiagent.agent.model.AgentState;
import org.springframework.ai.chat.messages.AssistantMessage;

public abstract class ReActAgent extends BaseAgent {
  public abstract boolean think();

  public abstract String act();

  @Override
  public String step() {
    boolean shouldAct = think(); // Failures propagate; they are not successful final answers.
    if (getState() != AgentState.RUNNING) return "";
    if (shouldAct) return act();
    var messages = getMessageList();
    if (messages.isEmpty()
        || !(messages.getLast() instanceof AssistantMessage answer)
        || answer.getText() == null
        || answer.getText().isBlank()) {
      throw new IllegalStateException("模型未返回有效回答");
    }
    setState(AgentState.FINISHED);
    return answer.getText();
  }
}
