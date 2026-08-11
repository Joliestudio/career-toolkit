package com.Jolie.career_toolkit.storage;

import java.io.InputStream;

/**
 * 檔案儲存。
 *
 * **這個 interface 才是交付物**，本機檔案系統只是目前的實作。
 * 之後要換成 S3 / MinIO 只換一個實作類別，Service 一行都不用動。
 *
 * storageKey 是伺服器自己產生的不透明字串，**跟使用者給的檔名完全無關**。
 * 直接拿使用者的檔名當路徑就是路徑穿越漏洞（`../../etc/passwd`）。
 */
public interface FileStorage {

    /** @return 之後要用來取回這個檔案的 key */
    String store(String storageKey, byte[] content);

    InputStream read(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
