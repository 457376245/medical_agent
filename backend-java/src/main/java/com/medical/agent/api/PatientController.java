package com.medical.agent.api;

import com.medical.agent.application.PatientService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.PatientCreateRequest;
import com.medical.agent.domain.dto.request.PatientUpdateRequest;
import com.medical.agent.domain.dto.response.EmptyData;
import com.medical.agent.domain.dto.response.PatientCreateResponseData;
import com.medical.agent.domain.dto.response.PatientListResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "病人管理", description = "病人（家人）管理接口")
public class PatientController {
  private final PatientService patientService;

  public PatientController(PatientService patientService) {
    this.patientService = patientService;
  }

  @GetMapping
  @Operation(summary = "查询病人列表", description = "返回当前用户下的所有病人")
  public ApiResponse<PatientListResponseData> list() {
    PatientListResponseData data = patientService.list();
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping
  @Operation(summary = "创建病人", description = "为当前用户添加新的病人（家人）")
  public ApiResponse<PatientCreateResponseData> create(@RequestBody PatientCreateRequest request) {
    PatientCreateResponseData data = patientService.create(request);
    return new ApiResponse<>("OK", "创建成功", RequestIdUtil.newRequestId(), data);
  }

  @PutMapping("/{patientId}")
  @Operation(summary = "更新病人信息", description = "修改病人的基本信息")
  public ApiResponse<EmptyData> update(
      @PathVariable("patientId") UUID patientId,
      @RequestBody PatientUpdateRequest request) {
    patientService.update(patientId, request);
    return new ApiResponse<>("OK", "更新成功", RequestIdUtil.newRequestId(), new EmptyData());
  }

  @DeleteMapping("/{patientId}")
  @Operation(summary = "删除病人", description = "删除指定病人，默认病人和有数据的病人不可删除")
  public ResponseEntity<ApiResponse<EmptyData>> delete(@PathVariable("patientId") UUID patientId) {
    patientService.delete(patientId);
    return ResponseEntity.ok(new ApiResponse<>("OK", "删除成功", RequestIdUtil.newRequestId(), new EmptyData()));
  }
}
