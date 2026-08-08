package com.gurukul.storage;

public record StoredFile(String url, String key, String contentType, long sizeBytes, String originalFilename) {
}
