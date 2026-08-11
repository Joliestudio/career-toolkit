package com.Jolie.career_toolkit.resume;

import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.resume.dto.SaveResumeVersionRequest;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ResumeService {

    /** gap-based 排序的間距。留 100 是為了「插到中間」不用重排全部。 */
    private static final int ORDER_GAP = 100;

    private final ResumeVersionRepository versionRepository;
    private final ResumeBlockRepository resumeBlockRepository;
    private final BlockRepository blockRepository;
    private final CurrentUser currentUser;

    public ResumeService(ResumeVersionRepository versionRepository,
                         ResumeBlockRepository resumeBlockRepository,
                         BlockRepository blockRepository,
                         CurrentUser currentUser) {
        this.versionRepository = versionRepository;
        this.resumeBlockRepository = resumeBlockRepository;
        this.blockRepository = blockRepository;
        this.currentUser = currentUser;
    }

    // ---------- 版本 ----------

    @Transactional
    public ResumeVersion create(SaveResumeVersionRequest request) {
        return versionRepository.save(
                new ResumeVersion(currentUser.id(), request.label(), request.targetRole()));
    }

    public List<ResumeVersion> list() {
        return versionRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(currentUser.id());
    }

    public ResumeVersion get(UUID id) {
        return versionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("ResumeVersion", id));
    }

    @Transactional
    public ResumeVersion update(UUID id, SaveResumeVersionRequest request) {
        ResumeVersion version = getEditable(id);
        if (request.label() != null) version.setLabel(request.label());
        if (request.targetRole() != null) version.setTargetRole(request.targetRole());
        return version;
    }

    @Transactional
    public void delete(UUID id) {
        get(id).markAsDeleted();
    }

    /**
     * 鎖定：把每個積木當下的實際內容凍結進 content_snapshot。
     *
     * 從這一刻起，改底層 block 不會再影響這份履歷。
     * 沒有這一步的話，「我三月寄給台積電的版本」會在你不知情的狀況下跟著變，
     * 而且無法還原——這是少數不做就永遠補不回來的東西。
     */
    @Transactional
    public ResumeVersion lock(UUID id) {
        ResumeVersion version = getEditable(id);

        List<ResumeBlock> entries = resumeBlockRepository
                .findByResumeVersionIdOrderBySortOrderAsc(id);
        Map<UUID, Block> blocks = blocksById(entries);

        for (ResumeBlock entry : entries) {
            Block block = blocks.get(entry.getBlockId());
            // 用 effectiveContent 而不是直接抓 block 內容：客製化過的措辭才是「當時真的寄出去的」
            entry.freeze(entry.effectiveContent(block != null ? block.getContent() : null));
        }

        version.lock();
        return version;
    }

    /**
     * 複製一份。鎖定的版本要改就走這條路——來源記在 parent_id。
     * 「針對這家再改一點」是求職的常態，記下演化關係之後才看得出各版本的差異從哪來。
     */
    @Transactional
    public ResumeVersion clone(UUID id, String newLabel) {
        ResumeVersion source = get(id);

        ResumeVersion copy = new ResumeVersion(
                currentUser.id(), newLabel, source.getTargetRole());
        copy.setParentId(source.getId());
        copy.setLocale(source.getLocale());
        versionRepository.save(copy);

        for (ResumeBlock entry : resumeBlockRepository.findByResumeVersionIdOrderBySortOrderAsc(id)) {
            ResumeBlock copied = new ResumeBlock(
                    copy.getId(), entry.getBlockId(), entry.getSortOrder());
            copied.setSection(entry.getSection());
            // 快照不複製：新版本是「活的」，要跟著底層 block 走，
            // 否則複製出來的東西一出生就被凍結，完全違反複製的意圖。
            copied.setContentOverride(entry.getContentOverride());
            resumeBlockRepository.save(copied);
        }

        return copy;
    }

    // ---------- 積木組裝 ----------

    @Transactional
    public ResumeBlock addBlock(UUID versionId, UUID blockId, String section) {
        getEditable(versionId);

        // 積木必須是自己的。少了這行就可以把別人的積木塞進自己的履歷。
        blockRepository.findByIdAndUserIdAndDeletedAtIsNull(blockId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));

        List<ResumeBlock> existing = resumeBlockRepository
                .findByResumeVersionIdOrderBySortOrderAsc(versionId);

        int nextOrder = existing.isEmpty()
                ? ORDER_GAP
                : existing.get(existing.size() - 1).getSortOrder() + ORDER_GAP;

        ResumeBlock entry = resumeBlockRepository
                .findByResumeVersionIdAndBlockId(versionId, blockId)
                .orElseGet(() -> new ResumeBlock(versionId, blockId, nextOrder));
        entry.setSection(section);

        return resumeBlockRepository.save(entry);
    }

    @Transactional
    public void removeBlock(UUID versionId, UUID blockId) {
        getEditable(versionId);
        resumeBlockRepository.deleteByResumeVersionIdAndBlockId(versionId, blockId);
    }

    /**
     * 重排。前端送一份完整的、已排好的 blockId 清單，這裡重新賦值 100/200/300…
     *
     * 用「整份重寫」而不是「兩兩交換」：交換的邏輯在中途失敗時會留下壞掉的順序，
     * 而整份重寫要嘛全成功要嘛整個 rollback。
     */
    @Transactional
    public List<ResumeBlock> reorder(UUID versionId, List<UUID> orderedBlockIds) {
        getEditable(versionId);

        Map<UUID, ResumeBlock> current = resumeBlockRepository
                .findByResumeVersionIdOrderBySortOrderAsc(versionId).stream()
                .collect(Collectors.toMap(ResumeBlock::getBlockId, Function.identity()));

        int order = ORDER_GAP;
        for (UUID blockId : orderedBlockIds) {
            ResumeBlock entry = current.get(blockId);
            if (entry == null) {
                throw new ResourceNotFoundException("ResumeBlock", blockId);
            }
            entry.setSortOrder(order);
            order += ORDER_GAP;
        }

        return resumeBlockRepository.findByResumeVersionIdOrderBySortOrderAsc(versionId);
    }

    @Transactional
    public ResumeBlock setOverride(UUID versionId, UUID blockId, String content) {
        getEditable(versionId);

        ResumeBlock entry = resumeBlockRepository
                .findByResumeVersionIdAndBlockId(versionId, blockId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeBlock", blockId));

        // 空字串代表「清除客製化，回到原文」——這跟 null（不要動）是不同的意圖
        entry.setContentOverride(content == null || content.isBlank() ? null : content);
        return entry;
    }

    // ---------- 渲染 ----------

    /**
     * 預覽與匯出走同一條路徑，所以「畫面上看到的」跟「匯出的」不可能不一致。
     */
    public List<RenderedBlock> render(UUID versionId) {
        get(versionId);

        List<ResumeBlock> entries = resumeBlockRepository
                .findByResumeVersionIdOrderBySortOrderAsc(versionId);
        Map<UUID, Block> blocks = blocksById(entries);

        List<RenderedBlock> rendered = new ArrayList<>();
        for (ResumeBlock entry : entries) {
            Block block = blocks.get(entry.getBlockId());
            if (block == null) continue;   // 積木被刪掉了（軟刪除），略過

            rendered.add(new RenderedBlock(
                    block.getId(),
                    block.getType(),
                    block.getTitle(),
                    entry.effectiveContent(block.getContent()),
                    entry.getSection(),
                    entry.getSortOrder(),
                    entry.getContentOverride() != null || entry.getContentSnapshot() != null));
        }

        rendered.sort(Comparator.comparingInt(RenderedBlock::sortOrder));
        return rendered;
    }

    // ---------- helpers ----------

    /**
     * 取出可編輯的版本——已鎖定的一律拒絕。
     * 集中在這裡而不是每個方法各自檢查，是為了「不可能漏掉」。
     */
    private ResumeVersion getEditable(UUID id) {
        ResumeVersion version = get(id);
        if (version.isLocked()) {
            throw new ResumeVersionLockedException(id);
        }
        return version;
    }

    /** 一次撈完，不要在迴圈裡一筆一筆查（N+1）。 */
    private Map<UUID, Block> blocksById(List<ResumeBlock> entries) {
        if (entries.isEmpty()) return Map.of();

        List<UUID> ids = entries.stream().map(ResumeBlock::getBlockId).toList();
        return blockRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Block::getId, Function.identity()));
    }
}
