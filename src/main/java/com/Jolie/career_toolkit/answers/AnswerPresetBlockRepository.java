package com.Jolie.career_toolkit.answers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** 由父實體（preset）限定；所有權在 Service 讀取 preset 時檢查。 */
public interface AnswerPresetBlockRepository
        extends JpaRepository<AnswerPresetBlock, AnswerPresetBlock.Key> {

    List<AnswerPresetBlock> findByPresetIdOrderBySortOrderAsc(UUID presetId);

    void deleteByPresetIdAndBlockId(UUID presetId, UUID blockId);
}
