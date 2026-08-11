package com.Jolie.career_toolkit.parsing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字典比對式的技能／證照抽取。
 *
 * 先做這個而不是先接 LLM，有三個理由：
 *   1. 零成本、可重現、無網路即可在 CI 跑
 *   2. 給日後的 LLM 一個「可量測的基準」——沒有基準就沒辦法判斷 LLM 值不值那筆錢
 *   3. 它現在就能用，不是佔位符
 *
 * 這是一個純函式類別：文字進、候選出，不碰資料庫也不碰 HTTP，
 * 所以測試可以直接對字串斷言。
 */
@Component
public class DictionaryExtractor {

    /** 記在候選上，日後才分得出哪些是字典抓的、哪些是 LLM 抓的。 */
    public static final String NAME = "DICTIONARY_V1";

    private final TermDictionary skills = TermDictionary.load("extraction/skills.txt");
    private final TermDictionary certifications = TermDictionary.load("extraction/certifications.txt");

    /** 一筆抽取結果。還沒進資料庫，所以不叫 Candidate。 */
    public record Match(
            ExtractionCandidate.Kind kind,
            /** 原文中實際出現的樣子 */
            String matched,
            /** 正規化後的標準名稱 */
            String canonical,
            BigDecimal confidence
    ) {}

    public List<Match> extract(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<Match> results = new ArrayList<>();
        results.addAll(matchAgainst(text, certifications, ExtractionCandidate.Kind.CERTIFICATION));
        results.addAll(matchAgainst(text, skills, ExtractionCandidate.Kind.SKILL));

        return results;
    }

    private List<Match> matchAgainst(String text, TermDictionary dictionary,
                                     ExtractionCandidate.Kind kind) {

        String haystack = TermDictionary.normalize(text);

        // 同一個正規名稱只留一筆——原文出現三次不該產生三個候選。
        // LinkedHashMap 讓輸出順序穩定，測試才好寫。
        Map<String, Match> byCanonical = new LinkedHashMap<>();

        for (String alias : dictionary.aliases()) {
            if (!containsAsWord(haystack, alias)) continue;

            String canonical = dictionary.canonicalOf(alias);

            // 信心度的粗略規則：別名越長，誤命中的機會越低。
            // "go" 兩個字很容易在別的詞裡出現，"spring boot" 幾乎不會。
            // 這個分數是給人看的排序依據，不是機率——所以刻意不裝作很精確。
            BigDecimal confidence = alias.length() >= 8 ? new BigDecimal("0.900")
                    : alias.length() >= 4 ? new BigDecimal("0.750")
                    : new BigDecimal("0.500");

            Match existing = byCanonical.get(canonical);
            if (existing == null || existing.confidence().compareTo(confidence) < 0) {
                byCanonical.put(canonical, new Match(kind, alias, canonical, confidence));
            }
        }

        return new ArrayList<>(byCanonical.values());
    }

    /**
     * 以「詞」為單位比對，不是單純的 contains。
     *
     * 沒有這個限制的話，「Go」會在「Google」「Django」「Mongo」裡命中，
     * 「C#」會在任何含 c 的地方命中。那種結果會讓整個候選清單失去可信度，
     * 使用者看兩次就再也不會用這個功能了。
     *
     * 中文沒有詞界（\b 對 CJK 無效），所以中文詞改用直接包含判斷——
     * 中文的技能名稱通常夠長，誤命中的風險遠低於英文縮寫。
     */
    private boolean containsAsWord(String haystack, String needle) {
        if (needle.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
            return haystack.contains(needle);
        }

        // (?<![\w#+.]) / (?![\w#+.]) 而不是 \b：
        // C#、C++、.NET、Node.js 這些名稱本身就含有 \b 會切開的字元
        String pattern = "(?<![\\w#+.])" + Pattern.quote(needle) + "(?![\\w#+.])";
        return Pattern.compile(pattern).matcher(haystack).find();
    }

    /** 給測試與診斷用。 */
    public int dictionarySize() {
        return skills.size() + certifications.size();
    }

    @SuppressWarnings("unused")
    private static String debug(Matcher m) {
        return m.group();
    }
}
