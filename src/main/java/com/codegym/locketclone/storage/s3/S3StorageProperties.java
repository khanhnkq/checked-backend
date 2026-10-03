package com.codegym.locketclone.storage.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "storage.s3")
public class S3StorageProperties {

    private String endpoint = "http://localhost:3900";
    private String publicBaseUrl = "http://localhost:3902/locket-photos";
    private String accessKeyId = "GK000000000000000000000001";
    private String secretAccessKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private String bucketName = "locket-photos";
    private String region = "garage";
}
