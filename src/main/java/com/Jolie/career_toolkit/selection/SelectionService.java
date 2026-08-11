package com.Jolie.career_toolkit.selection;

import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SelectionService {

    private static final String PURPOSE = "JD_BLOCK_SELECTION";

    private final BlockRepository blockRepository;
    private final SelectionCallRepository callRepository;
    private final BlockSelector selector;
    private final CurrentUser currentUser;

    /**
     * 每日上限。現在的選擇器零成本，這個限制看起來多餘——
     * 但等到接了會計費的 LLM 才想到要加，通常是因為看到帳單才想到。
     */
    private final int dailyQuota;

    public SelectionService(BlockRepository blockRepository,
                            SelectionCallRepository callRepository,
                            BlockSelector selector,
                            CurrentUser currentUser,
                            @Value("${app.selection.daily-quota:200}") int dailyQuota) {
        this.blockRepository = blockRepository;
        this.callRepository = callRepository;
        this.selector = selector;
        this.currentUser = currentUser;
        this.dailyQuota = dailyQuota;
    }

    /** 貼一份 JD，選出最相關的積木並附上理由。 */
    @Transactional
    public SelectionResult selectForJobDescription(String jobDescription) {
        UUID userId = currentUser.id();

        List<Block> candidates = blockRepository.findByUserIdAndDeletedAtIsNull(userId);
        String requestHash = hash(jobDescription, candidates);

        // 快取：同樣的 JD + 同樣的積木清單 → 結果一定相同。
        // 現在只省下幾毫秒的 CPU，接上 LLM 之後省下的是真的錢。
        boolean cached = callRepository.existsByUserIdAndRequestHash(userId, requestHash);

        if (!cached) {
            long usedToday = callRepository.countByUserIdSince(
                    userId, Instant.now().minus(1, ChronoUnit.DAYS));
            if (usedToday >= dailyQuota) {
                throw new QuotaExceededException(dailyQuota);
            }
        }

        long started = System.nanoTime();
        List<BlockSelector.Selection> selections = selector.select(jobDescription, candidates);
        int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);

        // 快取命中就不再記一筆——否則配額會被自己的重試吃光
        if (!cached) {
            callRepository.save(new SelectionCall(
                    userId, selector.name(), PURPOSE, requestHash,
                    latencyMs, candidates.size(), selections.size()));
        }

        Map<UUID, Block> byId = candidates.stream()
                .collect(Collectors.toMap(Block::getId, Function.identity()));

        List<SelectionResult.Item> items = selections.stream()
                .map(s -> {
                    Block block = byId.get(s.blockId());
                    return new SelectionResult.Item(
                            block.getId(), block.getType(), block.getTitle(), block.getContent(),
                            s.score(), s.reasons());
                })
                .toList();

        return new SelectionResult(selector.name(), cached, latencyMs, candidates.size(), items);
    }

    /**
     * hash(JD + 積木清單)。
     *
     * 依 id 排序，讓「同一組積木」不會因為查詢回傳順序不同而算出不同的 hash。
     * 帶上 updatedAt：積木內容改了就必須重算，不能回舊的快取——
     * 少了這一項，改完積木再貼同一份 JD 會拿到過期的推薦，而且完全看不出來。
     */
    private static String hash(String jobDescription, List<Block> blocks) {
        StringBuilder input = new StringBuilder(jobDescription == null ? "" : jobDescription);

        blocks.stream()
                .sorted(Comparator.comparing(b -> b.getId().toString()))
                .forEach(b -> input.append('|')
                        .append(b.getId())
                        .append('@')
                        .append(b.getUpdatedAt()));

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
