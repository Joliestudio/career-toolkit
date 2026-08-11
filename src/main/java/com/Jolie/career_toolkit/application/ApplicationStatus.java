package com.Jolie.career_toolkit.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 投遞狀態與它的合法轉換。
 *
 * 為什麼狀態機寫在這裡（Service 層）而不是資料庫：
 *   - DB 的 CHECK 約束看不到「前一個值」，只能檢查欄位本身合不合法，
 *     沒辦法表達「從 REJECTED 不能跳到 OFFER」。
 *   - trigger 做得到，但那會把商業邏輯藏在你永遠不會去找的地方。
 *     半年後有人問「為什麼這個狀態改不動」，沒有人會想到去翻 trigger。
 */
public enum ApplicationStatus {

    /** 還沒真的投出去，只是先記下來。 */
    DRAFT,
    APPLIED,
    SCREENING,
    INTERVIEWING,
    OFFER,

    ACCEPTED,
    DECLINED,
    REJECTED,
    WITHDRAWN,

    /**
     * 對方沒有下文。
     *
     * 這個狀態一定要有——它是最常見的真實結局，而幾乎每個天真的 schema 都會漏掉它。
     * 少了它，所有沒下文的投遞會永遠卡在 INTERVIEWING，
     * 「我的面試轉換率是多少」這種統計就整個失真。
     *
     * 而且它不是終態：對方過了三週突然回信是會發生的事。
     */
    GHOSTED;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DRAFT,        EnumSet.of(APPLIED, WITHDRAWN));

        // APPLIED 可以直接跳到 OFFER。
        // 狀態機要反映真實會發生的事，不是理想化的漏斗：
        // 小公司或內推的確會在一次面談後就給 offer，而且使用者本來就不會
        // 乖乖把狀態一關一關往下點——他會在收到 offer 的那一刻才想到要更新。
        // 少了這條路徑，「建立 offer」就沒辦法連動投遞狀態，兩邊會各說各話。
        ALLOWED.put(APPLIED,      EnumSet.of(SCREENING, INTERVIEWING, OFFER, REJECTED, GHOSTED, WITHDRAWN));
        ALLOWED.put(SCREENING,    EnumSet.of(INTERVIEWING, OFFER, REJECTED, GHOSTED, WITHDRAWN));
        ALLOWED.put(INTERVIEWING, EnumSet.of(OFFER, REJECTED, GHOSTED, WITHDRAWN));
        // OFFER 也可能被收回（REJECTED），這在市場轉壞時真的會發生
        ALLOWED.put(OFFER,        EnumSet.of(ACCEPTED, DECLINED, REJECTED, WITHDRAWN));

        // 對方突然回來了——GHOSTED 是唯一可以「復活」的狀態
        ALLOWED.put(GHOSTED,      EnumSet.of(SCREENING, INTERVIEWING, OFFER, REJECTED, WITHDRAWN));

        // 真正的終態
        ALLOWED.put(ACCEPTED,  EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(DECLINED,  EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(REJECTED,  EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    /** 這個狀態還「活著」嗎——用來算漏斗與提醒清單。 */
    public boolean isActive() {
        return !nextStates().isEmpty() || this == GHOSTED;
    }

    public boolean isTerminal() {
        return nextStates().isEmpty();
    }

    public Set<ApplicationStatus> nextStates() {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(ApplicationStatus.class));
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return nextStates().contains(target);
    }
}
