package com.tickethub.event.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Object storage settings for event posters (ST-30). Works with MinIO locally and AWS S3 in the cloud. */
@Data
@Component
@ConfigurationProperties(prefix = "tickethub.media")
public class MediaProperties {
    /** S3 API endpoint. Empty means "real AWS S3" (region based). */
    private String endpoint = "";
    private String region = "us-east-1";
    private String bucket = "tickethub-media";
    /** Static credentials. Leave empty on AWS to use the default credential chain (IAM role). */
    private String accessKey = "";
    private String secretKey = "";
    /** MinIO needs path-style addressing (host/bucket/key); AWS S3 works with either. */
    private boolean pathStyle = true;
    /** Base of the public URL stored on the event, e.g. http://localhost:9000/tickethub-media */
    private String publicBaseUrl = "http://localhost:9000/tickethub-media";
    private long maxSizeBytes = 5L * 1024 * 1024;
    /** ST-78: ask S3 to encrypt every poster at rest (SSE-S3, AES-256). Off locally (MinIO needs a KMS for it), on in the aws profile. */
    private boolean serverSideEncryption = false;
    /** ST-78: posters never change (the key contains a random id), so browsers/CDNs may cache them for a year. */
    private String cacheControl = "public, max-age=31536000, immutable";
}
