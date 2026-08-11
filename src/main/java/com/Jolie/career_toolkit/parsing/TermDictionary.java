package com.Jolie.career_toolkit.parsing;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 從資源檔載入的名詞字典：正規名稱 ← 多個別名。
 *
 * 別名為什麼重要：履歷上寫「k8s」跟寫「Kubernetes」是同一件事，
 * 但字面比對抓不到。沒有別名表的話，抽取結果會依使用者的書寫習慣而漂移，
 * 而那個漂移完全看不出來——你只會覺得「這份履歷好像沒什麼技能」。
 *
 * 字典放在資源檔而不是寫死在程式裡：擴充字典不需要改 Java，也不需要重新編譯。
 */
public class TermDictionary {

    /** 別名（小寫正規化後）→ 正規名稱。用 LinkedHashMap 保留檔案裡的順序，讓輸出穩定。 */
    private final Map<String, String> aliasToCanonical = new LinkedHashMap<>();

    private TermDictionary() {}

    public static TermDictionary load(String classpathResource) {
        TermDictionary dictionary = new TermDictionary();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathResource).getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|", 2);
                String canonical = parts[0].strip();
                if (canonical.isEmpty()) continue;

                // 正規名稱本身也是一個別名（履歷上通常就是這樣寫的）
                dictionary.aliasToCanonical.put(normalize(canonical), canonical);

                if (parts.length > 1) {
                    for (String alias : parts[1].split(",")) {
                        String trimmed = alias.strip();
                        if (!trimmed.isEmpty()) {
                            dictionary.aliasToCanonical.put(normalize(trimmed), canonical);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("載入字典失敗：" + classpathResource, e);
        }

        if (dictionary.aliasToCanonical.isEmpty()) {
            // 空字典會讓抽取永遠回傳零個候選，而且完全不會報錯——
            // 那是「靜默失效」的典型，寧可在啟動時就爆掉
            throw new IllegalStateException("字典是空的：" + classpathResource);
        }

        return dictionary;
    }

    /** 所有別名，依載入順序。 */
    public List<String> aliases() {
        return new ArrayList<>(aliasToCanonical.keySet());
    }

    public String canonicalOf(String normalizedAlias) {
        return aliasToCanonical.get(normalizedAlias);
    }

    public int size() {
        return aliasToCanonical.size();
    }

    /**
     * 比對用的正規化：轉小寫、把連字號與底線視同空白、壓縮連續空白。
     *
     * 這讓 "Spring-Boot"、"spring boot"、"SPRING  BOOT" 都對到同一個 key。
     * 不做這一步的話，字典要為每一種寫法各列一筆，很快就維護不動。
     */
    public static String normalize(String text) {
        return text.toLowerCase()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }
}
