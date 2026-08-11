package com.Jolie.career_toolkit.parsing;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 用 Tika 抽出檔案裡的純文字，並用 magic byte 判斷真正的檔案類型。
 *
 * **類型一定要用 magic byte 判斷，不能信副檔名或 Content-Type header**——
 * 那兩個都是使用者送什麼就是什麼。把 .exe 改名成 .pdf 是最基本的攻擊手法。
 */
@Component
public class TextExtractor {

    /** 履歷不會有一百萬字。設上限是為了擋住「解壓縮炸彈」那類的惡意檔案。 */
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    private final Tika tika;

    public TextExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_TEXT_LENGTH);
    }

    /** 依內容（magic byte）判斷真正的 MIME 類型，跟檔名無關。 */
    public String detectContentType(byte[] content, String hintFileName) {
        return tika.detect(content, hintFileName);
    }

    public record Result(String text, boolean hasTextLayer, String error) {

        public static Result ok(String text) {
            return new Result(text, true, null);
        }

        /**
         * 抽不出任何文字。
         *
         * 這跟「解析失敗」是不同的事：掃描檔（整頁都是圖片）本來就沒有文字層。
         * 混成同一種結果的話，使用者只會看到「解析失敗」然後不知道該怎麼辦，
         * 而正確的建議是「這是掃描檔，請提供可以複製文字的版本」。
         */
        public static Result noTextLayer() {
            return new Result("", false, null);
        }

        public static Result failed(String error) {
            return new Result("", false, error);
        }

        public boolean isFailure() {
            return error != null;
        }
    }

    public Result extract(byte[] content) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            String text = tika.parseToString(in);

            // Tika 對掃描檔會回傳空字串或只剩空白，不會拋例外。
            // 不特別判斷的話，使用者拿到的是「解析成功，抽到 0 個技能」——
            // 那個訊息完全沒有告訴他真正的原因。
            if (text == null || text.isBlank()) {
                return Result.noTextLayer();
            }

            return Result.ok(text);

        } catch (IOException | TikaException e) {
            // 訊息只進 log 與資料庫的 parse_error，不直接回給前端——
            // Tika 的例外訊息會帶出檔案結構與內部類別名
            return Result.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
