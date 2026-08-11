package com.Jolie.career_toolkit.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 結構性防護：每個 repository 都必須被明確分類，而且方法名要符合該類別的所有權規則。
 *
 * 為什麼需要這個測試：
 * 行為測試（A 讀不到 B 的資料）只能驗證「已經寫好的端點」。當有人在半年後
 * 加了一個 findByType(BlockType type) 然後在新的 Service 裡用它，
 * 行為測試不會有任何反應——因為那個新端點還沒有對應的測試。
 *
 * 這個測試守的是規則本身，不是規則的某一次應用。
 *
 * 為什麼是「分類」而不是單純的「方法名要含 UserId」：
 * P3 加進來的面試、offer、狀態歷程都是掛在投遞底下的子實體，
 * 它們的所有權是在讀取父實體時檢查的（Service 一律先跑 applicationService.get()），
 * 方法名裡自然不會有 UserId。用一條規則硬套的話，只能不斷加長例外清單，
 * 而例外清單長到某個程度就等於把防護關掉了。
 *
 * 分類版本反而更嚴格：新增一個 repository 卻沒有分類，測試會直接失敗，
 * 強迫你先想清楚「這張表的所有權是怎麼成立的」再繼續寫。
 *
 * 純反射，不需要啟動 Spring，跑起來是毫秒等級。
 */
class RepositoryOwnershipConventionTest {

    private static final String BASE_PACKAGE = "com.Jolie.career_toolkit";

    /** 使用者直屬的資料：所有權條件必須寫進方法名。 */
    private static final Set<String> USER_OWNED = Set.of(
            "BlockRepository",
            "JobApplicationRepository",
            "ResumeVersionRepository",
            "ResumeFileRepository",
            "ProjectRepository",
            "AnswerPresetRepository",
            "SelectionCallRepository"
    );

    /**
     * 由父實體限定的子實體：方法名必須帶得出父實體的 id（或直接帶 UserId）。
     *
     * 這一類的所有權保證有一半在 Service 層——呼叫前一定先跑過父實體的 get()，
     * 那一步會做真正的 userId 比對。反射看不到那件事，所以這裡只能守住
     * 「查詢至少被某個 id 限定住」，剩下的靠 *ControllerTest 的跨帳號測試。
     */
    private static final Map<String, Set<String>> PARENT_SCOPED = Map.of(
            "ApplicationStatusHistoryRepository", Set.of("UserId", "ApplicationId"),
            "InterviewRepository",                Set.of("UserId", "ApplicationId"),
            "ReviewRepository",                   Set.of("UserId", "ApplicationId", "InterviewId"),
            "OfferRepository",                    Set.of("UserId", "ApplicationId"),
            "ResumeBlockRepository",              Set.of("UserId", "ResumeVersionId"),
            "ExtractionCandidateRepository",      Set.of("UserId", "ResumeFileId"),
            "AnswerPresetBlockRepository",        Set.of("UserId", "PresetId")
    );

    /**
     * 全域參照資料：不屬於任何使用者，所以沒有所有權可綁。
     *
     * 公司是全站共用的目錄（願景裡有後台管理與產業分類，那只有在共享目錄上才有意義），
     * 產業是參照表。要放進這一類，前提是「這張表不含任何使用者的私人資料」。
     */
    private static final Set<String> GLOBAL_REFERENCE = Set.of(
            "CompanyRepository",
            "IndustryRepository",
            "QuestionTypeRepository"
    );

    /**
     * 身分本身。users 是租戶的根，它沒有更上層的擁有者可以綁，
     * 而且登入流程必須能用 email 查到人，否則沒有人能登入。
     */
    private static final Set<String> IDENTITY = Set.of(
            "UserRepository"
    );

    @Test
    void everyRepositoryMustBeClassified() {
        List<String> unclassified = new ArrayList<>();

        for (Class<?> repository : findRepositoryInterfaces()) {
            String name = repository.getSimpleName();
            boolean known = USER_OWNED.contains(name)
                    || PARENT_SCOPED.containsKey(name)
                    || GLOBAL_REFERENCE.contains(name)
                    || IDENTITY.contains(name);

            if (!known) unclassified.add(name);
        }

        assertThat(unclassified)
                .withFailMessage("""
                        These repositories are not classified: %s

                        每個 repository 都必須歸到下列其中一類（見本檔案上方的常數）：
                          USER_OWNED       使用者直屬 —— 方法名必須含 UserId
                          PARENT_SCOPED    子實體    —— 方法名必須含父實體的 id
                          GLOBAL_REFERENCE 全域參照  —— 不含任何使用者私人資料
                          IDENTITY         身分本身

                        先想清楚「這張表的所有權是怎麼成立的」再加進去，
                        不要為了讓測試變綠就隨便挑一類。
                        """, unclassified)
                .isEmpty();
    }

    @Test
    void userOwnedRepositories_mustScopeEveryMethodToUserId() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repository : findRepositoryInterfaces()) {
            if (!USER_OWNED.contains(repository.getSimpleName())) continue;

            for (Method method : repository.getDeclaredMethods()) {
                if (!method.getName().contains("UserId")) {
                    violations.add(describe(repository, method));
                }
            }
        }

        assertThat(violations)
                .withFailMessage("""
                        These methods do not scope to UserId: %s

                        所有權條件必須出現在方法名裡（例如 findByIdAndUserIdAndDeletedAtIsNull）。
                        原因是 Spring Data 依「方法名」推導查詢——參數列有 userId 但方法名沒有的話，
                        那個參數會被靜默忽略，不會報錯，只會回傳別人的資料。
                        """, violations)
                .isEmpty();
    }

    @Test
    void parentScopedRepositories_mustScopeEveryMethodToAnOwnerOrParentId() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repository : findRepositoryInterfaces()) {
            Set<String> allowedScopes = PARENT_SCOPED.get(repository.getSimpleName());
            if (allowedScopes == null) continue;

            for (Method method : repository.getDeclaredMethods()) {
                boolean scoped = allowedScopes.stream().anyMatch(method.getName()::contains);
                if (!scoped) violations.add(describe(repository, method) + " 需要含 " + allowedScopes);
            }
        }

        assertThat(violations)
                .withFailMessage("""
                        These methods are not scoped by an owner or parent id: %s

                        子實體的查詢至少要被父實體的 id 限定住，Service 端再負責檢查那個父實體是不是你的。
                        沒有任何限定條件的查詢會跨越所有使用者。
                        """, violations)
                .isEmpty();
    }

    /**
     * 全域參照資料是唯一可以完全不帶所有權條件的類別，所以它必須一直維持很小。
     * 這個清單變長，通常代表某張本來該有 user_id 的表被錯放進來了。
     */
    @Test
    void theGlobalReferenceListShouldStaySmall() {
        assertThat(GLOBAL_REFERENCE).hasSizeLessThanOrEqualTo(4);
        assertThat(IDENTITY).hasSizeLessThanOrEqualTo(2);
    }

    private static String describe(Class<?> repository, Method method) {
        String params = String.join(", ",
                java.util.Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).toList());
        return repository.getSimpleName() + "#" + method.getName() + "(" + params + ")";
    }

    private static List<Class<?>> findRepositoryInterfaces() {
        // 預設的掃描器只收「具體類別」，repository 都是介面，所以要覆寫判斷條件
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("掃描到但載入不了：" + definition.getBeanClassName(), e);
            }
        }

        // 掃不到任何 repository 一定是掃描設定壞了。
        // 沒有這個斷言的話，這個測試會在「什麼都沒檢查」的狀態下永遠綠燈——
        // 那比沒有測試更危險，因為它給人一種有防護的錯覺。
        assertThat(found)
                .withFailMessage("掃描不到任何 repository，檢查 BASE_PACKAGE 設定")
                .isNotEmpty();

        // 同理：分類清單裡列了不存在的 repository（改名或刪掉之後忘了更新）也要抓出來，
        // 否則那一筆分類會永遠不生效而沒人發現。
        Set<String> actual = new TreeSet<>(found.stream().map(Class::getSimpleName).toList());
        Set<String> classified = new TreeSet<String>();
        classified.addAll(USER_OWNED);
        classified.addAll(PARENT_SCOPED.keySet());
        classified.addAll(GLOBAL_REFERENCE);
        classified.addAll(IDENTITY);
        classified.removeAll(actual);

        assertThat(classified)
                .withFailMessage("分類清單裡有不存在的 repository（可能已改名或刪除）: %s", classified)
                .isEmpty();

        return found;
    }
}
