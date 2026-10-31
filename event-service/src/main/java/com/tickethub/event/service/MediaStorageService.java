package com.tickethub.event.service;

import com.tickethub.event.common.exception.BusinessRuleException;
import com.tickethub.event.common.exception.DependencyUnavailableException;
import com.tickethub.event.config.MediaProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Stores event media in S3-compatible object storage (ST-30). The S3 client is created on first use,
 * so the service starts even when the storage is not running.
 */
@Slf4j
@Service
public class MediaStorageService {

    private static final Map<String, String> ALLOWED_TYPES =
            Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private final MediaProperties properties;
    private volatile S3Client client;

    public MediaStorageService(MediaProperties properties) {
        this.properties = properties;
    }

    /** @return the public URL of the stored poster */
    public String storePoster(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("A non-empty image file is required");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessRuleException("Poster must be a JPEG, PNG or WebP image");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessRuleException("Poster must be at most " + (properties.getMaxSizeBytes() / 1024 / 1024) + " MB");
        }
        String key = "posters/" + eventId + "/" + UUID.randomUUID() + extension;
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .cacheControl(properties.getCacheControl());
            if (properties.isServerSideEncryption()) {
                request.serverSideEncryption(ServerSideEncryption.AES256); // ST-78: encrypted at rest
            }
            client().putObject(request.build(), RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException | RuntimeException e) {
            log.error("Poster upload failed for event {}: {}", eventId, e.toString());
            throw new DependencyUnavailableException("Media storage is unavailable. Please try again shortly.");
        }
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        log.info("Poster stored for event {} key={}", eventId, key);
        return base + "/" + key;
    }

    private S3Client client() {
        S3Client c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    var builder = S3Client.builder()
                            .region(Region.of(properties.getRegion()))
                            .httpClient(UrlConnectionHttpClient.create())
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(properties.isPathStyle()).build());
                    if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                        builder.endpointOverride(URI.create(properties.getEndpoint()));
                    }
                    if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()) {
                        builder.credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
                    }
                    c = builder.build();
                    client = c;
                }
            }
        }
        return c;
    }
}
