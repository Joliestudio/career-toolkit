package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.block.dto.BlockResponse;
import com.Jolie.career_toolkit.block.dto.CreateBlockRequest;
import com.Jolie.career_toolkit.block.dto.UpdateBlockRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping
    public ResponseEntity<BlockResponse> createBlock(@Valid @RequestBody CreateBlockRequest request) {
        Block block = blockService.createBlock(request);

        // 201 的回應要帶 Location header 指向新資源——這是 HTTP 的約定，
        // 客戶端才不用自己拼 URL。
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(block.getId())
                .toUri();

        return ResponseEntity.created(location).body(BlockResponse.from(block));
    }

    @GetMapping
    public List<BlockResponse> getBlocks(@RequestParam(required = false) BlockType type) {
        return blockService.getBlocks(type).stream()
                .map(BlockResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BlockResponse getBlock(@PathVariable UUID id) {
        return BlockResponse.from(blockService.getBlock(id));
    }

    // 用 PATCH 而不是 PUT：PUT 的語意是「整個資源替換」，沒給的欄位應該變成 null。
    // 表單場景是部分更新，用 PUT 會讓「只改標題」把內容清空。
    @PatchMapping("/{id}")
    public BlockResponse updateBlock(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateBlockRequest request) {
        return BlockResponse.from(blockService.updateBlock(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBlock(@PathVariable UUID id) {
        blockService.deleteBlock(id);
        return ResponseEntity.noContent().build();
    }
}
