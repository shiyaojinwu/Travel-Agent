package com.sz.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 🧠 FileBasedChatMemory —— 基于文件持久化的对话记忆实现类
 * 实现 Spring AI 的 ChatMemory 接口，将对话记录持久化存储到本地文件系统中，
 * 使用 Kryo 作为序列化/反序列化框架，支持高效、二进制格式存储。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Slf4j
@SuppressWarnings("unused")
public class FileBasedChatMemory implements ChatMemory {

    /**
     * 存储对话文件的目录路径
     */
    private final String BASE_DIR;

    /**
     * Kryo 序列化对象（线程不安全，这里静态只适合单线程使用）
     */
    private static final Kryo kryo = new Kryo();

    static {
        // 允许不注册类也能序列化（更灵活）
        kryo.setRegistrationRequired(false);
        // 设置对象实例化策略，避免类缺少无参构造函数时报错
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * 构造函数：创建本地存储目录（如果不存在就新建）
     * @param dir 本地存储路径
     * @author zyh
     * @date 2025/07/13
     */
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            boolean created = baseDir.mkdirs();
            if (created) {
                log.info("📁 本地目录 [{}] 不存在，已成功创建", dir);
            } else {
                log.warn("⚠️ 本地目录 [{}] 创建失败，可能会影响会话持久化", dir);
            }
        } else {
            log.info("📂 使用已有目录 [{}] 保存对话记录", dir);
        }
    }

    /**
     * 添加一段消息记录到指定会话中
     * @param conversationId 会话 ID
     * @param messages       消息列表
     * @author zyh
     * @date 2025/07/13
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        // 获取已有的对话消息（如果没有则初始化为空列表）
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        // 添加新消息
        conversationMessages.addAll(messages);
        // 持久化保存
        saveConversation(conversationId, conversationMessages);
    }

    /**
     * 获取最近 N 条对话消息
     * @param conversationId 会话 ID
     * @param lastN          获取的消息条数
     * @return 最近 N 条消息
     * @author zyh
     * @date 2025/07/13
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = getOrCreateConversation(conversationId);
        if (allMessages.isEmpty()) {
            log.info("📭 会话 [{}] 当前无历史消息记录", conversationId);
            return List.of(); // 返回不可变空列表
        }
        int total = allMessages.size();
        int fromIndex = Math.max(0, total - lastN);
        List<Message> recentMessages = allMessages.subList(fromIndex, total);
        log.info("📨 会话 [{}] 获取最近 [{}] 条消息（共 [{}] 条）", conversationId, recentMessages.size(), total);
        return recentMessages;
    }


    /**
     * 清除某个会话的所有消息（即删除对应文件）
     * @param conversationId 会话 ID
     * @author zyh
     * @date 2025/07/13
     */
    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        // 确保是文件，防止误删目录
        if (file.exists() && file.isFile()) {
            if (file.delete()) {
                log.info("🗑️ 会话 [{}] 的缓存文件 [{}] 已成功删除", conversationId, file.getName());
            } else {
                log.warn("⚠️ 尝试删除会话 [{}] 的缓存文件 [{}] 失败", conversationId, file.getName());
            }
        } else {
            log.info("📁 会话 [{}] 的缓存文件不存在，无需删除", conversationId);
        }
    }


    /**
     * 从本地文件中读取已有会话消息；如果文件不存在，返回空列表
     * @param conversationId 会话 ID
     * @return 消息列表
     * @author zyh
     * @date 2025/07/13
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            // 文件不存在，返回空记录
            log.info("📂 会话文件 [{}] 不存在，初始化空对话列表", file.getName());
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            // 反序列化消息对象（注意泛型类型安全）
            @SuppressWarnings("unchecked")
            List<Message> messages = kryo.readObject(input, ArrayList.class);
            return messages;
        } catch (IOException | RuntimeException e) {
            log.error("❌ 读取会话 [{}] 失败：{}", conversationId, e.getMessage(), e);
            // 出错也返回空列表，避免影响系统继续运行
            return new ArrayList<>();
        }
    }

    /**
     * 将消息列表序列化并写入到本地文件
     * @param conversationId 会话 ID
     * @param messages       消息列表
     * @author zyh
     * @date 2025/07/13
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        File tempFile = new File(file.getAbsolutePath() + ".tmp");

        try (Output output = new Output(new FileOutputStream(tempFile))) {
            // 写入临时文件
            kryo.writeObject(output, messages);
            output.flush();
        } catch (IOException e) {
            log.error("❌ 会话 [{}] 写入临时文件失败：{}", conversationId, e.getMessage(), e);
            return; // 直接返回，后续不执行
        }

        try {
            // 用复制替代重命名，保证替换原文件
            Files.copy(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempFile.toPath());
            log.info("✅ 会话 [{}] 成功保存到文件 [{}]", conversationId, file.getName());
        } catch (IOException e) {
            log.error("❌ 会话 [{}] 保存失败（复制替代重命名）：{}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 获取当前会话对应的本地文件路径
     * @param conversationId 会话 ID
     * @return File 对象
     * @author zyh
     * @date 2025/07/13
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
