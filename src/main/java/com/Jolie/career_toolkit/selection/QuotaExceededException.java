package com.Jolie.career_toolkit.selection;

/**
 * 超過每日配額。
 *
 * 現在的選擇器是純本機的、零成本，理論上不需要配額。
 * 但這個機制要從第一天就在：等到接了會計費的 LLM 才想到要加，
 * 通常是因為看到帳單才想到——那時候錢已經花掉了。
 */
public class QuotaExceededException extends RuntimeException {

    private final int limit;

    public QuotaExceededException(int limit) {
        super("Daily selection quota exceeded: " + limit);
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
}
