package com.medical.agent.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockUploadController {

  @PutMapping("/mock-upload/{*objectKey}")
  public ResponseEntity<Void> mockUpload(
      @PathVariable("objectKey") String objectKey,
      @RequestBody(required = false) byte[] body) {
    return ResponseEntity.ok().build();
  }
}
