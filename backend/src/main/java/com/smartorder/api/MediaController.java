package com.smartorder.api;

import com.smartorder.service.ImageService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
  private final ImageService imageService;

  public MediaController(ImageService imageService) {
    this.imageService = imageService;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, String> upload(@RequestPart("file") MultipartFile file) throws Exception {
    return imageService.uploadDishImage(file.getBytes());
  }
}
