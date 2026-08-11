package com.Jolie.career_toolkit.block;

import java.util.UUID;

/**
 * Service 層不應該知道 HTTP 的存在。
 *
 * 用自訂例外而不是 ResponseStatusException，是為了讓「找不到積木」這件事保持成領域概念——
 * 之後 GlobalExceptionHandler 才是唯一決定它對應到哪個 HTTP 狀態碼的地方。
 * 這樣同一個 Service 也能被排程任務、CLI、訊息佇列呼叫，而不會莫名其妙拋出 HTTP 例外。
 */
public class BlockNotFoundException extends RuntimeException {

    private final UUID blockId;

    public BlockNotFoundException(UUID blockId) {
        super("Block not found: " + blockId);
        this.blockId = blockId;
    }

    public UUID getBlockId() {
        return blockId;
    }
}
