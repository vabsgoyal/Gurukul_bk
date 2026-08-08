package com.gurukul.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

	/**
	 * Uploads the file under a key derived from keyPrefix and returns where it landed.
	 * Throws IllegalArgumentException for oversized/disallowed files, IllegalStateException if
	 * storage isn't configured.
	 */
	StoredFile upload(MultipartFile file, String keyPrefix);

}
