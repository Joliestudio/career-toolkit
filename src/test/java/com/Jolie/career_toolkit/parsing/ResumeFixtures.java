package com.Jolie.career_toolkit.parsing;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 用程式產生測試用的履歷檔。
 *
 * 為什麼不放真實檔案進 repo：真實履歷含個資，而且二進位檔在 code review 時看不出改了什麼。
 * 程式產生的 fixture 每個位元組都是這裡寫出來的，出問題時查得到原因。
 *
 * 注意這些 fixture 全部是 ASCII。中文 PDF 需要嵌入 CJK 字型，
 * 而「CJK 子集字型缺 ToUnicode 表會抽出亂碼」這件事沒辦法用合成檔案重現——
 * 那需要真實世界的壞檔案。所以中文解析的品質必須靠手動驗證，測試涵蓋不到。
 */
final class ResumeFixtures {

    private ResumeFixtures() {}

    static byte[] plainText(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** 有文字層的正常 PDF。 */
    static byte[] pdfWithText(String text) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                // showText 不吃換行，一行一行寫
                for (String line : text.split("\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
            }

            document.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 只有圖片、沒有文字層的 PDF —— 模擬掃描檔。
     *
     * 這是 P5 最重要的 fixture：抽不出東西時系統必須明確說「這是掃描檔」，
     * 而不是回一句「解析成功，找到 0 個技能」讓使用者一頭霧水。
     */
    static byte[] scannedPdfWithoutTextLayer() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 400, 200);
            g.setColor(Color.BLACK);
            // 畫幾條線，看起來像掃描出來的文字，但沒有任何可抽取的字元
            for (int y = 40; y < 180; y += 24) {
                g.drawLine(30, y, 30 + (y * 2 % 300), y);
            }
            g.dispose();

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdImage, 60, 500, 400, 200);
            }

            document.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] docx(String text) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String line : text.split("\n")) {
                document.createParagraph().createRun().setText(line);
            }

            document.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 把執行檔改名成 .pdf —— 最基本的攻擊手法，必須被擋下來。 */
    static byte[] windowsExecutable() {
        byte[] content = new byte[512];
        // MZ header：Windows PE 執行檔的 magic byte
        content[0] = 'M';
        content[1] = 'Z';
        return content;
    }
}
