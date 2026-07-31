package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional // 解決卡點：每個測試跑完自動 rollback，確保資料庫不會互相污染
public class BlockRepositoryTest extends IntegrationTestBase {

    @Autowired
    private BlockRepository blockRepository;

    // 直接使用 Flyway V2 塞入的開發者 UUID，這樣寫入 block 就不會違反 FK 限制
    private final UUID devUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void saveAndFindById_shouldReturnSameBlock() {
        // Arrange (準備資料)
        Block block = new Block(devUserId, BlockType.SKILL, "Java", "Spring Boot 開發");

        // Act (執行動作)
        Block saved = blockRepository.save(block);
        Optional <Block> found = blockRepository.findById(saved.getId());

        // Assert (驗證結果)
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Java");
        assertThat(found.get().getContent()).isEqualTo("Spring Boot 開發");
        assertThat(found.get().getType()).isEqualTo(BlockType.SKILL);
    }

    @Test
    void findByUserIdAndDeletedAtIsNull_shouldNotReturnSoftDeletedBlocks() {
        // Arrange
        Block activeBlock = new Block(devUserId, BlockType.SKILL, "使用中", "這筆是活著的");
        Block deletedBlock = new Block(devUserId, BlockType.EXPERIENCE, "已刪除", "這筆被刪了");
        deletedBlock.markAsDeleted(); // 標記為軟刪除

        blockRepository.save(activeBlock);
        blockRepository.save(deletedBlock);

        // Act
        List<Block> results = blockRepository.findByUserIdAndDeletedAtIsNull(devUserId);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("使用中");
    }

    @Test
    void save_shouldAutoPopulateCreatedAtAndUpdatedAt() {
        // Arrange
        Block block = new Block(devUserId, BlockType.PROJECT, "專案 A", "自動填寫時間測試");

        // Act
        // 使用 saveAndFlush 確保 Hibernate 立刻將資料寫入資料庫並觸發資料庫的時間戳記機制
        Block saved = blockRepository.saveAndFlush(block);

        // Assert
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}