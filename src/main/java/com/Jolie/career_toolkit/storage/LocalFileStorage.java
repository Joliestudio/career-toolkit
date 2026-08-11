package com.Jolie.career_toolkit.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存在本機磁碟（容器裡是一個 named volume）。
 *
 * 為什麼不存進資料庫的 bytea：
 *   - 每次 pg_dump 都會把所有履歷檔一起倒出來，備份體積爆炸
 *   - 一個不小心的 SELECT * 就把整批二進位資料拉進記憶體
 *   - 檔案沒有交易語意的需求，硬塞進資料庫只是把問題搬家
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.local-root:/var/lib/career-toolkit/files}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("無法建立儲存目錄：" + this.root, e);
        }
    }

    @Override
    public String store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("寫入檔案失敗：" + storageKey, e);
        }
    }

    @Override
    public InputStream read(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("讀取檔案失敗：" + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("刪除檔案失敗：" + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    /**
     * 把 key 解析成實際路徑，並確認結果沒有跑到 root 外面。
     *
     * key 是伺服器產生的，理論上不會有 `..`。但這個檢查還是要有——
     * 「理論上不會發生」的假設一旦哪天被新的呼叫方打破，
     * 沒有這道檢查就是任意檔案寫入。防護的成本是三行，代價是整台機器。
     */
    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("storage key 逃出了儲存根目錄：" + storageKey);
        }
        return resolved;
    }
}
