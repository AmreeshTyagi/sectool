package com.secfix.todos.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String endpoint;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner,
                          @Value("${sectool.s3.bucket}") String bucket,
                          @Value("${sectool.s3.endpoint}") String endpoint) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    @PostConstruct
    public void init() {
        ensureBucketExists();
    }

    public String buildObjectKey(UUID tenantId, UUID documentId, UUID versionId, String suffix) {
        return String.format("tenant/%s/documents/%s/versions/%s/%s", tenantId, documentId, versionId, suffix);
    }

    public String generatePresignedPutUrl(String objectKey, String contentType, Duration expiration) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putRequest)
                .build();

        return toPathStyleUrl(s3Presigner.presignPutObject(presignRequest).url());
    }

    public String generatePresignedGetUrl(String objectKey, Duration expiration) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getRequest)
                .build();

        return toPathStyleUrl(s3Presigner.presignGetObject(presignRequest).url());
    }

    /**
     * AWS SDK presigner may generate virtual-hosted-style URLs (bucket.host/key)
     * even with pathStyleAccessEnabled. This converts them to path-style (host/bucket/key)
     * which is required by S3-compatible stores like rustfs.
     */
    private String toPathStyleUrl(java.net.URL presignedUrl) {
        String url = presignedUrl.toString();
        URI endpointUri = URI.create(endpoint);
        String endpointHost = endpointUri.getHost();

        // If the URL host is "bucket.endpointHost", rewrite to path-style
        String virtualHost = bucket + "." + endpointHost;
        if (presignedUrl.getHost().equals(virtualHost)) {
            url = url.replace("://" + virtualHost, "://" + endpointHost + ":" + endpointUri.getPort() + "/" + bucket);
        }
        return url;
    }

    public void putObject(String objectKey, byte[] data, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    public byte[] getObject(String objectKey) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build()).asByteArray();
    }

    public void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            logger.info("Creating bucket: {}", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
