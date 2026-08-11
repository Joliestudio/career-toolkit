package com.Jolie.career_toolkit.answers;

import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.interview.ReviewRepository;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnswerPresetService {

    private static final int ORDER_GAP = 100;

    private final AnswerPresetRepository presetRepository;
    private final AnswerPresetBlockRepository presetBlockRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final BlockRepository blockRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUser currentUser;

    public AnswerPresetService(AnswerPresetRepository presetRepository,
                               AnswerPresetBlockRepository presetBlockRepository,
                               QuestionTypeRepository questionTypeRepository,
                               BlockRepository blockRepository,
                               ReviewRepository reviewRepository,
                               CurrentUser currentUser) {
        this.presetRepository = presetRepository;
        this.presetBlockRepository = presetBlockRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.blockRepository = blockRepository;
        this.reviewRepository = reviewRepository;
        this.currentUser = currentUser;
    }

    public List<QuestionType> questionTypes() {
        return questionTypeRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional
    public AnswerPreset create(Short questionTypeId, String label, Integer targetCharLimit) {
        questionTypeRepository.findById(questionTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionType", null));

        return presetRepository.save(new AnswerPreset(
                currentUser.id(), questionTypeId, label, targetCharLimit));
    }

    public List<AnswerPreset> list() {
        return presetRepository.findByUserIdOrderByQuestionTypeIdAsc(currentUser.id());
    }

    public AnswerPreset get(UUID id) {
        return presetRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("AnswerPreset", id));
    }

    @Transactional
    public AnswerPreset update(UUID id, String label, Integer targetCharLimit, String notes) {
        AnswerPreset preset = get(id);
        if (label != null) preset.setLabel(label);
        if (targetCharLimit != null) preset.setTargetCharLimit(targetCharLimit);
        if (notes != null) preset.setNotes(notes);
        return preset;
    }

    @Transactional
    public void delete(UUID id) {
        presetRepository.delete(get(id));
    }

    // ---------- 積木組裝 ----------

    @Transactional
    public void addBlock(UUID presetId, UUID blockId) {
        get(presetId);

        // 積木必須是自己的
        blockRepository.findByIdAndUserIdAndDeletedAtIsNull(blockId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));

        List<AnswerPresetBlock> existing = presetBlockRepository
                .findByPresetIdOrderBySortOrderAsc(presetId);

        boolean alreadyThere = existing.stream().anyMatch(e -> e.getBlockId().equals(blockId));
        if (alreadyThere) return;

        int nextOrder = existing.isEmpty()
                ? ORDER_GAP
                : existing.get(existing.size() - 1).getSortOrder() + ORDER_GAP;

        presetBlockRepository.save(new AnswerPresetBlock(presetId, blockId, nextOrder));
    }

    @Transactional
    public void removeBlock(UUID presetId, UUID blockId) {
        get(presetId);
        presetBlockRepository.deleteByPresetIdAndBlockId(presetId, blockId);
    }

    @Transactional
    public void reorder(UUID presetId, List<UUID> orderedBlockIds) {
        get(presetId);

        Map<UUID, AnswerPresetBlock> current = presetBlockRepository
                .findByPresetIdOrderBySortOrderAsc(presetId).stream()
                .collect(Collectors.toMap(AnswerPresetBlock::getBlockId, Function.identity()));

        int order = ORDER_GAP;
        for (UUID blockId : orderedBlockIds) {
            AnswerPresetBlock entry = current.get(blockId);
            if (entry == null) throw new ResourceNotFoundException("AnswerPresetBlock", blockId);
            entry.setSortOrder(order);
            order += ORDER_GAP;
        }
    }

    /**
     * 組出答案。
     *
     * 這是題型對應真正的產出：把選好的積木依序串起來，
     * 使用者直接複製貼進對方的表單。字數用 code point 算，跟前端計數器一致。
     */
    public AssembledAnswer assemble(UUID presetId) {
        AnswerPreset preset = get(presetId);

        List<AnswerPresetBlock> entries = presetBlockRepository
                .findByPresetIdOrderBySortOrderAsc(presetId);

        Map<UUID, Block> blocks = entries.isEmpty() ? Map.of()
                : blockRepository.findAllById(entries.stream().map(AnswerPresetBlock::getBlockId).toList())
                .stream().collect(Collectors.toMap(Block::getId, Function.identity()));

        List<AssembledAnswer.Part> parts = new ArrayList<>();
        for (AnswerPresetBlock entry : entries) {
            Block block = blocks.get(entry.getBlockId());
            if (block == null || block.getDeletedAt() != null) continue;   // 積木已刪除
            parts.add(new AssembledAnswer.Part(
                    block.getId(), block.getTitle(), block.getContent(), entry.getSortOrder()));
        }
        parts.sort(Comparator.comparingInt(AssembledAnswer.Part::sortOrder));

        String text = parts.stream()
                .map(AssembledAnswer.Part::content)
                .collect(Collectors.joining("\n\n"));

        return new AssembledAnswer(
                preset.getId(), preset.getLabel(), preset.getTargetCharLimit(),
                parts, text, countChars(text));
    }

    /**
     * 從面試檢討裡撈出真正被問過的題目，當作準備的參考。
     *
     * 這條迴路是整個系統最有價值的地方：P3 記下來的 questions_asked，
     * 在這裡變成下次要準備的題目。沒有這條線，檢討就只是寫完不會再看的日記。
     */
    public List<String> questionsAskedInPastInterviews() {
        return reviewRepository.findQuestionsAskedByUserId(currentUser.id()).stream()
                .filter(q -> q != null && !q.isBlank())
                .flatMap(q -> java.util.Arrays.stream(q.split("\\r?\\n")))
                .map(String::strip)
                .filter(q -> !q.isEmpty())
                // LinkedHashSet 去重但保留順序，最近的檢討排在前面
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private static int countChars(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
