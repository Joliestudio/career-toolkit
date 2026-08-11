package com.Jolie.career_toolkit.resume;

import java.util.UUID;

/**
 * 已鎖定的履歷版本不能再修改。
 *
 * 這不是「保護使用者不要手滑」，而是鎖定這個機制本身的意義：
 * 鎖定之後這份履歷就代表「我當時真的寄出去的東西」。允許事後修改的話，
 * 那個保證就消失了，快照也就沒有意義。
 *
 * 想改就複製一份新的（clone），來源會記在 parent_id 裡。
 */
public class ResumeVersionLockedException extends RuntimeException {

    public ResumeVersionLockedException(UUID id) {
        super("Resume version is locked: " + id);
    }
}
