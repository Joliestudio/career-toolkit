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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 結構性防護：掃描所有 repository，斷言每個自訂查詢方法名都含有 UserId。
 *
 * 為什麼需要這個測試：
 * 行為測試（A 讀不到 B 的資料）只能驗證「已經寫好的端點」。當有人在半年後
 * 加了一個 findByType(BlockType type) 然後在新的 Service 裡用它，
 * 行為測試不會有任何反應——因為那個新端點還沒有對應的測試。
 *
 * 這個測試會在「方法被加進 repository 的那一刻」就失敗，而不是等到上線後才發現。
 * 它守的是規則本身，不是規則的某一次應用。
 *
 * 不需要啟動 Spring，純反射，跑起來是毫秒等級。
 */
class RepositoryOwnershipConventionTest {

    private static final String BASE_PACKAGE = "com.Jolie.career_toolkit";

    /**
     * 例外清單。每一項都必須寫清楚理由——
     * 這個清單變長就是設計出問題的訊號，不該無意識地往下加。
     */
    private static final Set<String> EXEMPT = Set.of(
            // users 表本身就是租戶的根，它沒有「更上層的擁有者」可以綁。
            // 登入流程必須能用 email 查到人，否則沒有人能登入。
            "UserRepository#findByEmailIgnoreCase",
            "UserRepository#existsByEmailIgnoreCase"
    );

    @Test
    void everyCustomRepositoryMethod_shouldScopeToUserId() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repository : findRepositoryInterfaces()) {
            for (Method method : repository.getDeclaredMethods()) {
                String key = repository.getSimpleName() + "#" + method.getName();

                if (EXEMPT.contains(key)) {
                    continue;
                }
                if (!method.getName().contains("UserId")) {
                    violations.add(key + "(" + describeParams(method) + ")");
                }
            }
        }

        assertThat(violations)
                .withFailMessage("""
                        以下 repository 方法沒有把所有權條件寫進方法名：

                          %s

                        所有權條件必須出現在方法名裡（例如 findByIdAndUserIdAndDeletedAtIsNull），
                        原因是 Spring Data 依「方法名」推導查詢——參數列有 userId 但方法名沒有的話，
                        那個參數會被靜默忽略，不會報錯，只會回傳別人的資料。

                        如果某個方法真的不需要綁使用者，請加進 EXEMPT 清單並寫明理由。
                        """, String.join("\n  ", violations))
                .isEmpty();
    }

    @Test
    void theExemptionListShouldStaySmall() {
        // 這個上限沒有魔法，就是一個會逼人停下來想一下的閾值。
        // 例外變多通常代表某個地方的資料模型漏了 user_id。
        assertThat(EXEMPT).hasSizeLessThanOrEqualTo(6);
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

        return found;
    }

    private static String describeParams(Method method) {
        return String.join(", ",
                java.util.Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName)
                        .toList());
    }
}
