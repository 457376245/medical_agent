package com.medical.agent.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "调试上传", description = "本地调试用 Mock 上传接口")
public class MockUploadController {

  @PutMapping("/mock-upload/{*objectKey}")
  @Operation(
      summary = "Mock 上传二进制内容",
      description = "用于本地联调场景，直接返回 200",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = false,
          description = "二进制文件内容",
          content = @Content(mediaType = "application/octet-stream", examples = @ExampleObject(value = "<binary>"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "上传成功（无响应体）")
  })
  public ResponseEntity<Void> mockUpload(
      @Parameter(description = "对象键（含路径）", example = "uploads/a1/report.pdf")
      @PathVariable("objectKey") String objectKey,
      @RequestBody(required = false) byte[] body) {
    return ResponseEntity.ok().build();
  }
}
