package com.codegym.locketclone.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    UploadedFile uploadPhoto(MultipartFile file) throws IOException;

    UploadedFile uploadAvatar(MultipartFile file) throws IOException;

    void deleteFile(String key);
}
