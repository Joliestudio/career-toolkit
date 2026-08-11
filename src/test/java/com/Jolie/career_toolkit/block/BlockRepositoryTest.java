package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

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
    void findByType_shouldReturnOnlyThatType() {
        // Arrange：同一個使用者、兩種不同 type
        blockRepository.save(new Block(devUserId, BlockType.SKILL, "技能", "Java"));
        blockRepository.save(new Block(devUserId, BlockType.EXPERIENCE, "經歷", "後端工程師"));

        // Act
        List<Block> results = blockRepository.findByUserIdAndTypeAndDeletedAtIsNull(devUserId, BlockType.SKILL);

        // Assert
        // 這個測試存在的理由：原本的方法名是 findByUserIdAndDeletedAtIsNull(UUID, BlockType)，
        // 只宣告了 UserId 與 DeletedAtIsNull 兩個條件。Spring Data 依「方法名」推導查詢，
        // 多出來的 type 參數被靜默忽略 —— 不會拋錯，只是回傳未篩選的 2 筆。
        // 條件必須寫進方法名才會生效。
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(BlockType.SKILL);
    }

    @Test
    void findByIdAndUserId_shouldNotReturnOtherUsersBlock() {
        // Arrange
        Block mine = blockRepository.save(new Block(devUserId, BlockType.SKILL, "我的", "只有我看得到"));
        UUID someoneElse = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

        // Act
        Optional<Block> asOwner = blockRepository.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), devUserId);
        Optional<Block> asStranger = blockRepository.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), someoneElse);

        // Assert：用別人的 userId 查同一個 id 必須查不到 —— 這是 P1 權限隔離的基礎
        assertThat(asOwner).isPresent();
        assertThat(asStranger).isEmpty();
    }

    @Test
    void tags_shouldRoundTripThroughJsonbColumn() {
        // Arrange
        Block block = new Block(devUserId, BlockType.SKILL, "Java", "內容");
        block.setTags(List.of("backend", "spring"));
        UUID id = blockRepository.saveAndFlush(block).getId();

        // 清掉一級快取，強制真的從資料庫重讀——否則 findById 只會把同一個
        // Java 物件還給你，根本沒有經過 JSONB 的序列化與反序列化。
        entityManager.clear();

        // Act
        Block reloaded = blockRepository.findById(id).orElseThrow();

        // Assert：tags 欄位在 DB 早就存在，但 entity 之前沒映射它。
        // ddl-auto: validate 不會抓到這種漏映射，因為它只檢查「entity 有的欄位 DB 也要有」。
        assertThat(reloaded.getTags()).containsExactly("backend", "spring");
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