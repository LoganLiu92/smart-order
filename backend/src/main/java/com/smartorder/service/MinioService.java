package com.smartorder.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioService {
  private final MinioClient client;
  private final String bucket;
  private final String endpoint;

  public MinioService(
      @Value("${app.minio.endpoint}") String endpoint,
      @Value("${app.minio.accessKey}") String accessKey,
      @Value("${app.minio.secretKey}") String secretKey,
      @Value("${app.minio.bucket:smart-order}") String bucket) {
    this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    this.bucket = bucket;
    this.endpoint = endpoint;
  }

  public String upload(String objectName, InputStream input, long size, String contentType) throws Exception {
    ensureBucket();
    client.putObject(
        PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .stream(input, size, -1)
            .contentType(contentType)
            .build());
    return endpoint + "/" + bucket + "/" + objectName;
  }

  private void ensureBucket() throws Exception {
    boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }
}
