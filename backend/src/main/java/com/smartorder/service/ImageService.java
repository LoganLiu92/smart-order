package com.smartorder.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageService {
  private final MinioService minioService;
  private final int thumbSize;
  private final int detailSize;

  public ImageService(MinioService minioService,
                      @Value("${app.minio.thumb-size:320}") int thumbSize,
                      @Value("${app.minio.detail-size:900}") int detailSize) {
    this.minioService = minioService;
    this.thumbSize = thumbSize;
    this.detailSize = detailSize;
  }

  public Map<String, String> uploadDishImage(byte[] bytes) throws Exception {
    String base = "dish/" + UUID.randomUUID();
    byte[] thumb = resize(bytes, thumbSize);
    byte[] detail = resize(bytes, detailSize);

    String thumbUrl = minioService.upload(base + "-thumb.jpg", new ByteArrayInputStream(thumb), thumb.length, "image/jpeg");
    String detailUrl = minioService.upload(base + "-detail.jpg", new ByteArrayInputStream(detail), detail.length, "image/jpeg");
    return Map.of("thumbnailUrl", thumbUrl, "detailUrl", detailUrl);
  }

  private byte[] resize(byte[] bytes, int size) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Thumbnails.of(new ByteArrayInputStream(bytes))
        .crop(Positions.CENTER)
        .size(size, size)
        .outputFormat("jpg")
        .outputQuality(0.85)
        .toOutputStream(output);
    return output.toByteArray();
  }
}
