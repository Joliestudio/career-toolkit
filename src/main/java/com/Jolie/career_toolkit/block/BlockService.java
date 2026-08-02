package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.block.dto.CreateBlockRequest;
import com.Jolie.career_toolkit.block.dto.UpdateBlockRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 類別層先宣告 readOnly = true 當預設，寫入的方法各自用 @Transactional 覆寫。
 * 這樣「忘記加註解」的預設結果是唯讀（安全），而不是意外開了一個可寫交易。
 *
 * @Transactional 的兩個致命細節：
 *   1. 只在 public method 上有效——Spring 用代理實作它，private/protected 上的註解
 *      完全不會生效，而且不會有任何警告。
 *   2. 同一個類別內部呼叫不會經過代理。deleteBlock() 裡呼叫 this.getBlock()，
 *      套用的是 deleteBlock 自己的交易設定（可寫），getBlock 上的 readOnly 不會生效——
 *      這裡剛好是我們要的，但這個行為必須是刻意的，不能是碰巧。
 */
@Service
@Transactional(readOnly = true)
public class BlockService {

    // TODO P1: 換成從 SecurityContext 取得的登入使用者
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // 建構子注入而不是 @Autowired 欄位注入：依賴變成必填（少給一個編譯就不過），
    // 欄位可以是 final，而且單元測試裡可以直接 new，不需要啟動 Spring。
    private final BlockRepository blockRepository;

    public BlockService(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Transactional
    public Block createBlock(CreateBlockRequest request) {
        Block block = new Block(DEV_USER_ID, request.type(), request.title(), request.content());
        return blockRepository.save(block);
    }

    public List<Block> getBlocks(BlockType type) {
        return type == null
                ? blockRepository.findByUserIdAndDeletedAtIsNull(DEV_USER_ID)
                : blockRepository.findByUserIdAndTypeAndDeletedAtIsNull(DEV_USER_ID, type);
    }

    /**
     * 用 findByIdAndUserIdAndDeletedAtIsNull 而不是 findById() 再自己 filter。
     * 差別在於所有權條件是在 SQL 的 WHERE 裡，不是撈回來之後才在記憶體裡篩——
     * 前者不可能忘記，後者只要少寫一行 filter 就是跨帳號讀取。
     */
    public Block getBlock(UUID id) {
        return blockRepository.findByIdAndUserIdAndDeletedAtIsNull(id, DEV_USER_ID)
                .orElseThrow(() -> new BlockNotFoundException(id));
    }

    /**
     * PATCH 是部分更新：null 代表「這個欄位不要動」，不是「把它清空」。
     * 如果寫成無條件覆寫，使用者只改標題就會把內容洗掉。
     */
    @Transactional
    public Block updateBlock(UUID id, UpdateBlockRequest request) {
        Block block = getBlock(id);

        if (request.title() != null) {
            block.setTitle(request.title());
        }
        if (request.content() != null) {
            block.updateContent(request.content());
        }

        // 不需要呼叫 save()：block 是交易中的 managed entity，
        // Hibernate 的 dirty checking 會在交易提交時自動寫回。
        // （這正是為什麼這個方法不能標 readOnly——readOnly 會關掉 dirty checking，
        //   結果就是「沒有錯誤訊息，但資料沒有更新」。）
        return block;
    }

    /** 軟刪除：只填上 deletedAt，不執行 repository.delete()。 */
    @Transactional
    public void deleteBlock(UUID id) {
        getBlock(id).markAsDeleted();
    }
}
