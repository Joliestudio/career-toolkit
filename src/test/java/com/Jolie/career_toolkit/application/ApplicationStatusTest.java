package com.Jolie.career_toolkit.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.Jolie.career_toolkit.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 狀態機的純單元測試。不需要 Spring 也不需要資料庫。
 *
 * 狀態機是 P3 的核心規則，值得用比整合測試快幾百倍的方式把每一條路徑釘住。
 */
class ApplicationStatusTest {

    @Test
    void happyPath_shouldBeReachableStepByStep() {
        assertThat(DRAFT.canTransitionTo(APPLIED)).isTrue();
        assertThat(APPLIED.canTransitionTo(SCREENING)).isTrue();
        assertThat(SCREENING.canTransitionTo(INTERVIEWING)).isTrue();
        assertThat(INTERVIEWING.canTransitionTo(OFFER)).isTrue();
        assertThat(OFFER.canTransitionTo(ACCEPTED)).isTrue();
        assertThat(OFFER.canTransitionTo(DECLINED)).isTrue();
    }

    /**
     * 狀態機要反映真實會發生的事，不是理想化的漏斗。
     * 小公司或內推會在一次面談後就給 offer；而且使用者本來就不會乖乖把狀態
     * 一關一關往下點，他會在收到 offer 的那一刻才想到要更新。
     */
    @Test
    void offerCanArriveDirectlyFromApplied() {
        assertThat(APPLIED.canTransitionTo(OFFER)).isTrue();
    }

    @Test
    void shouldNotBeAbleToSkipBackwards() {
        assertThat(INTERVIEWING.canTransitionTo(APPLIED)).isFalse();
        assertThat(OFFER.canTransitionTo(SCREENING)).isFalse();
        assertThat(SCREENING.canTransitionTo(DRAFT)).isFalse();
    }

    @Test
    void rejectedIsTerminal() {
        assertThat(REJECTED.isTerminal()).isTrue();
        assertThat(REJECTED.nextStates()).isEmpty();
        // 這是最容易誤會的一條：被拒絕之後不會又突然拿到 offer
        assertThat(REJECTED.canTransitionTo(OFFER)).isFalse();
    }

    @Test
    void acceptedAndDeclinedAreTerminal() {
        assertThat(ACCEPTED.isTerminal()).isTrue();
        assertThat(DECLINED.isTerminal()).isTrue();
        assertThat(WITHDRAWN.isTerminal()).isTrue();
    }

    /**
     * GHOSTED 是唯一可以「復活」的狀態。
     *
     * 對方隔了三週突然回信是真的會發生的事。如果把 GHOSTED 也設成終態，
     * 使用者只能刪掉重建，那筆投遞的完整歷程就斷了。
     */
    @Test
    void ghostedIsNotTerminal_becauseCompaniesDoComeBack() {
        assertThat(GHOSTED.isTerminal()).isFalse();
        assertThat(GHOSTED.canTransitionTo(INTERVIEWING)).isTrue();
        assertThat(GHOSTED.canTransitionTo(OFFER)).isTrue();
        assertThat(GHOSTED.canTransitionTo(REJECTED)).isTrue();
    }

    @Test
    void ghostedShouldBeReachableFromEveryLiveStage() {
        // 少了任何一條，該階段的投遞就會永遠卡著，漏斗統計跟著失真
        assertThat(APPLIED.canTransitionTo(GHOSTED)).isTrue();
        assertThat(SCREENING.canTransitionTo(GHOSTED)).isTrue();
        assertThat(INTERVIEWING.canTransitionTo(GHOSTED)).isTrue();
    }

    @Test
    void offerCanBeRescinded() {
        // 市場轉壞時 offer 真的會被收回
        assertThat(OFFER.canTransitionTo(REJECTED)).isTrue();
    }

    @Test
    void withdrawnShouldBeReachableFromEveryLiveStage() {
        assertThat(DRAFT.canTransitionTo(WITHDRAWN)).isTrue();
        assertThat(APPLIED.canTransitionTo(WITHDRAWN)).isTrue();
        assertThat(SCREENING.canTransitionTo(WITHDRAWN)).isTrue();
        assertThat(INTERVIEWING.canTransitionTo(WITHDRAWN)).isTrue();
        assertThat(OFFER.canTransitionTo(WITHDRAWN)).isTrue();
    }

    /**
     * 每個狀態都必須有明確的定義，不能靠 getOrDefault 的預設值矇混過去。
     * 新增一個狀態卻忘了在 ALLOWED 裡登記的話，它會靜默變成終態——
     * 那是「沒有錯誤訊息但行為錯誤」的典型。
     */
    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void everyStatusMustBeExplicitlyDeclared(ApplicationStatus status) {
        boolean isKnownTerminal = switch (status) {
            case ACCEPTED, DECLINED, REJECTED, WITHDRAWN -> true;
            default -> false;
        };

        if (isKnownTerminal) {
            assertThat(status.nextStates()).isEmpty();
        } else {
            assertThat(status.nextStates())
                    .withFailMessage("%s 沒有任何合法的下一步，若它不該是終態，"
                            + "表示 ApplicationStatus.ALLOWED 裡漏了登記", status)
                    .isNotEmpty();
        }
    }

    @Test
    void transitionErrorShouldNameTheLegalAlternatives() {
        IllegalStatusTransitionException ex = new IllegalStatusTransitionException(REJECTED, OFFER);

        // 只說「不能這樣改」的話，使用者只能一個一個試
        assertThat(ex.describeAllowed()).contains("終態");

        IllegalStatusTransitionException fromApplied =
                new IllegalStatusTransitionException(APPLIED, ACCEPTED);
        assertThat(fromApplied.describeAllowed())
                .contains("SCREENING")
                .contains("INTERVIEWING")
                .contains("REJECTED");
    }
}
