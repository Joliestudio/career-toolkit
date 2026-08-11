package com.Jolie.career_toolkit.selection;

import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.parsing.DictionaryExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 純本機的關鍵字／標籤選擇器。零成本、無網路、完全可測。
 *
 * 這不是佔位符——它現在就能用。做成「真的能用的實作」而不是空殼，
 * 意義在於 LLM 變成純粹的升級選項，不是前提：
 * 沒有 API key 的人也拿得到一個會動的功能，而接上 LLM 之後
 * 可以在 selection_calls 表裡直接比較兩者的效果。
 *
 * 加權的邏輯很簡單，而且刻意保持簡單：
 *   標題命中 > 標籤命中 > 內文命中
 * 因為標題是你自己下的摘要，命中標題代表這塊積木「整體」就是在講那件事；
 * 內文命中可能只是順帶提了一句。
 */
@Component
public class KeywordBlockSelector implements BlockSelector {

    public static final String NAME = "KEYWORD_V1";

    private static final double TITLE_WEIGHT = 3.0;
    private static final double TAG_WEIGHT = 2.0;
    private static final double CONTENT_WEIGHT = 1.0;

    /** 分數低於這個值就不推薦——寧可少給幾個，也不要給一堆勉強沾邊的。 */
    private static final double MIN_SCORE = 1.0;

    private final DictionaryExtractor dictionaryExtractor;

    public KeywordBlockSelector(DictionaryExtractor dictionaryExtractor) {
        this.dictionaryExtractor = dictionaryExtractor;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Selection> select(String jobDescription, List<Block> candidates) {
        if (jobDescription == null || jobDescription.isBlank() || candidates.isEmpty()) {
            return List.of();
        }

        // 用跟履歷解析同一套字典抽出 JD 裡的技術名詞。
        // 共用字典的好處：JD 寫 k8s、你的積木寫 Kubernetes，一樣對得上。
        Set<String> jdTerms = new LinkedHashSet<>();
        dictionaryExtractor.extract(jobDescription)
                .forEach(m -> jdTerms.add(m.canonical()));

        // 字典之外的一般關鍵字（職稱、領域詞…）。字典只涵蓋技術名詞，
        // 「後端」「資料工程」「新創」這類詞也很重要。
        jdTerms.addAll(significantWords(jobDescription));

        List<Selection> selections = new ArrayList<>();

        for (Block block : candidates) {
            double score = 0;
            List<String> reasons = new ArrayList<>();

            String title = lower(block.getTitle());
            String content = lower(block.getContent());
            List<String> tags = block.getTags() == null ? List.of()
                    : block.getTags().stream().map(KeywordBlockSelector::lower).toList();

            for (String term : jdTerms) {
                String needle = lower(term);
                boolean hit = false;

                if (title.contains(needle)) {
                    score += TITLE_WEIGHT;
                    hit = true;
                } else if (tags.stream().anyMatch(t -> t.contains(needle))) {
                    score += TAG_WEIGHT;
                    hit = true;
                } else if (content.contains(needle)) {
                    score += CONTENT_WEIGHT;
                    hit = true;
                }

                if (hit) reasons.add(term);
            }

            if (score >= MIN_SCORE) {
                selections.add(new Selection(block.getId(), score, reasons));
            }
        }

        selections.sort(Comparator.comparingDouble(Selection::score).reversed());
        return selections;
    }

    /**
     * 從 JD 抽出「看起來有意義」的詞。
     *
     * 判斷方式很粗略：長度夠、不是停用詞。這裡刻意不做斷詞——
     * 中文斷詞要嘛引入一整包相依，要嘛自己寫一個不準的版本，
     * 而這個功能的價值主要來自字典命中，一般詞只是補充。
     * 把它做得更複雜，投報率很低。
     */
    private static Set<String> significantWords(String text) {
        Set<String> words = new LinkedHashSet<>();

        for (String raw : text.split("[\\s,、。，；;:：/／（）()\\[\\]【】\\n\\r]+")) {
            String word = raw.strip();
            if (word.length() < 2 || word.length() > 20) continue;
            if (STOP_WORDS.contains(lower(word))) continue;
            // 純數字（年資、薪資）沒有比對價值
            if (word.chars().allMatch(Character::isDigit)) continue;

            words.add(word);
        }

        return words;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "and", "the", "for", "with", "you", "your", "our", "will", "are", "have",
            "工作", "職務", "公司", "團隊", "我們", "以上", "熟悉", "具備", "經驗", "相關",
            "負責", "能力", "要求", "條件", "加分", "歡迎", "應徵", "職缺", "薪資", "福利"
    );

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
