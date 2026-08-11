package com.Jolie.career_toolkit.parsing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 純單元測試，不需要 Spring 也不需要資料庫。
 *
 * 抽取的邊界條件比想像中多，而且錯了不會報錯——只會讓候選清單變得沒有可信度，
 * 使用者看兩次就再也不用這個功能了。所以每一條規則都要有測試釘住。
 */
class DictionaryExtractorTest {

    private final DictionaryExtractor extractor = new DictionaryExtractor();

    private List<String> canonicalsIn(String text) {
        return extractor.extract(text).stream().map(DictionaryExtractor.Match::canonical).toList();
    }

    @Test
    void dictionaryShouldActuallyLoad() {
        // 空字典會讓抽取永遠回零個候選而且完全不報錯——那是靜默失效的典型
        assertThat(extractor.dictionarySize()).isGreaterThan(100);
    }

    @Test
    void shouldFindPlainSkillNames() {
        assertThat(canonicalsIn("熟悉 Java 與 PostgreSQL，使用 Docker 部署"))
                .contains("Java", "PostgreSQL", "Docker");
    }

    /**
     * 別名是這個字典存在的主要理由。
     * 履歷上寫 k8s 跟寫 Kubernetes 是同一件事，但字面比對抓不到。
     */
    @Test
    void shouldResolveAliasesToCanonicalNames() {
        assertThat(canonicalsIn("熟悉 k8s 與 JS")).contains("Kubernetes", "JavaScript");
        assertThat(canonicalsIn("Golang 後端")).contains("Go");
        assertThat(canonicalsIn("使用 SpringBoot 開發")).contains("Spring Boot");
    }

    @Test
    void shouldNormalizeHyphensAndCasing() {
        assertThat(canonicalsIn("SPRING-BOOT")).contains("Spring Boot");
        assertThat(canonicalsIn("spring boot")).contains("Spring Boot");
        assertThat(canonicalsIn("Spring_Boot")).contains("Spring Boot");
    }

    /**
     * 這一組是整個抽取器最重要的測試。
     *
     * 沒有詞界限制的話，「Go」會在 Google、Django、Mongo 裡命中，
     * 「C#」會在任何含 c 的地方命中。那種結果會讓整份候選清單失去可信度。
     */
    @Test
    void shouldNotMatchSubstringsInsideOtherWords() {
        assertThat(canonicalsIn("我在 Google 工作，用 Django 和 MongoDB"))
                .doesNotContain("Go");

        assertThat(canonicalsIn("負責 Jenkins 維護"))
                .doesNotContain("Java");   // 沒有 Java，不該因為別的字命中
    }

    /**
     * 句尾的詞要抓得到。
     *
     * 這個 bug 是在端到端驗收時才發現的：JD 寫「... and PostgreSQL.」，
     * 而最初的詞界把句點當成詞的一部分，於是整句話裡的 PostgreSQL 被漏掉。
     * 單元測試沒抓到，是因為當時的測試字串剛好都沒有句點結尾——
     * 這正是端到端驗證不能被單元測試取代的理由。
     */
    @Test
    void shouldMatchTermsFollowedByPunctuation() {
        assertThat(canonicalsIn("Requires Java, Spring Boot and PostgreSQL."))
                .contains("Java", "Spring Boot", "PostgreSQL");

        assertThat(canonicalsIn("熟悉 Docker、Kubernetes。")).contains("Docker", "Kubernetes");
        assertThat(canonicalsIn("會用 Redis！")).contains("Redis");
        assertThat(canonicalsIn("(React)")).contains("React");
    }

    /** 名稱本身含有標點的技能不能被詞界切開。 */
    @Test
    void shouldMatchNamesContainingPunctuation() {
        assertThat(canonicalsIn("使用 C# 開發")).contains("C#");
        assertThat(canonicalsIn("Node.js 後端")).contains("Node.js");
        assertThat(canonicalsIn("熟 C++")).contains("C++");
        assertThat(canonicalsIn(".NET Core 經驗")).contains(".NET");
    }

    /** 中文沒有詞界（\b 對 CJK 無效），所以改用直接包含判斷。 */
    @Test
    void shouldMatchChineseTerms() {
        assertThat(canonicalsIn("有微服務架構經驗")).contains("Microservices");
        assertThat(canonicalsIn("熟悉敏捷開發流程")).contains("Agile");
    }

    @Test
    void shouldDeduplicateRepeatedMentions() {
        List<String> found = canonicalsIn("Java Java Java 還有 JDK");

        // 原文出現四次（含別名），只該產生一個候選
        assertThat(found.stream().filter("Java"::equals)).hasSize(1);
    }

    @Test
    void shouldFindCertifications() {
        List<DictionaryExtractor.Match> matches = extractor.extract("持有 AWS SAA 與 PMP 證照，多益 900 分");

        assertThat(matches)
                .filteredOn(m -> m.kind() == ExtractionCandidate.Kind.CERTIFICATION)
                .extracting(DictionaryExtractor.Match::canonical)
                .contains("AWS Certified Solutions Architect", "PMP", "TOEIC");
    }

    @Test
    void shouldClassifySkillsAndCertificationsSeparately() {
        List<DictionaryExtractor.Match> matches = extractor.extract("熟悉 Docker，持有 CKA 證照");

        assertThat(matches)
                .filteredOn(m -> m.canonical().equals("Docker"))
                .allMatch(m -> m.kind() == ExtractionCandidate.Kind.SKILL);

        assertThat(matches)
                .filteredOn(m -> m.canonical().equals("Certified Kubernetes Administrator"))
                .allMatch(m -> m.kind() == ExtractionCandidate.Kind.CERTIFICATION);
    }

    /**
     * 信心度是給人看的排序依據，不是機率。
     * 短別名（Go、JS）誤命中的機會比長別名（Spring Boot）高得多，分數要反映這件事。
     */
    @Test
    void longerMatchesShouldHaveHigherConfidence() {
        var springBoot = extractor.extract("Spring Boot").get(0);
        var go = extractor.extract("Go").get(0);

        assertThat(springBoot.confidence()).isGreaterThan(go.confidence());
    }

    @Test
    void emptyOrNullTextShouldReturnNothing() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("")).isEmpty();
        assertThat(extractor.extract("   \n  ")).isEmpty();
    }

    @Test
    void textWithNoKnownTermsShouldReturnNothing() {
        assertThat(extractor.extract("我喜歡爬山和攝影，週末會去看展覽")).isEmpty();
    }
}
