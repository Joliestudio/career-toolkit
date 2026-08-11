package com.Jolie.career_toolkit.common;

import java.util.UUID;

/**
 * 找不到資源。
 *
 * 「不屬於你」也一律用這個，不要另外拋權限不足——
 * 403 等於確認「這個 id 真的存在」，可以拿來逐一探測哪些 id 有效。
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " not found: " + id);
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }
}
