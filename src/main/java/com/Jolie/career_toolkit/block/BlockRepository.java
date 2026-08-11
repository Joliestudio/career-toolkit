package com.Jolie.career_toolkit.block;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 命名慣例：每個方法都必須帶 userId，方法名也必須含 UserId。
 *
 * 理由：Spring Data 是依「方法名」推導查詢的，多出來的參數會被靜默忽略而不報錯
 * （findByUserIdAndDeletedAtIsNull(UUID, BlockType) 就是這樣回傳了未篩選的結果）。
 * 把所有權條件寫進方法名，是唯一能讓編譯器與測試看得見它的方式。
 * RepositoryNamingConventionTest 會自動檢查這條規則。
 */
public interface BlockRepository extends JpaRepository<Block, UUID> {

    List<Block> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Block> findByUserIdAndTypeAndDeletedAtIsNull(UUID userId, BlockType type);

    Optional<Block> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
