package com.sz.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.sz.aiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * ✅ PDF 生成工具
 * 支持将文本内容生成 PDF 文件并保存到本地，默认保存路径为 /pdf 目录。
 * 使用 iText 进行 PDF 文档创建，支持中文字体。
 * ⚙️ 可由 Spring AI 工具系统通过工具调用自动生成文档。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class PDFGenerationTool {

    /**
     * 生成 PDF 文件
     * @param fileName PDF 文件保存的文件名（如 result.pdf）
     * @param content  要写入 PDF 的文本内容（如用户生成报告、摘要等）
     * @return PDF 文件保存路径或错误信息
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "根据输入内容生成 PDF 文件", returnDirect = true)
    public String generatePDF(
            @ToolParam(description = "保存生成 PDF 文件的文件名") String fileName,
            @ToolParam(description = "写入 PDF 的文本内容") String content) {
        // 构建保存目录和目标文件路径
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录（若不存在）
            FileUtil.mkdir(fileDir);

            // 使用 iText 创建 PDF 文档并写入内容
            try (PdfWriter writer = new PdfWriter(filePath);    // 创建 PDF 写入器
                 PdfDocument pdf = new PdfDocument(writer);     // 创建 PDF 文档对象
                 Document document = new Document(pdf)) {       // 创建文档容器
                // 设置中文字体，防止乱码
                // 若使用本地字体，可手动指定路径加载字体
                // String fontPath = Paths.get("src/main/resources/static/fonts/simsun.ttf").toAbsolutePath().toString();
                // PdfFont font = PdfFontFactory.createFont(fontPath, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                // 使用 iText 内置的中文字体（STSongStd-Light）
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                // 创建段落对象并添加到 PDF 中
                Paragraph paragraph = new Paragraph(content);
                document.add(paragraph);
            }
            // 返回成功提示及保存路径
            log.info("PDF 创建成功，保存路径为：{}", filePath);
            return "PDF 生成成功，保存路径为：" + filePath;
        } catch (IOException e) {
            // 异常处理
            log.error("PDF 创建失败，原因：{}", e.getMessage());
            return "PDF 生成失败，错误信息：" + e.getMessage();
        }
    }
}
