package com.medical.agent.api;

import com.medical.agent.application.TimelineService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.TimelineProfileDetailResponseData;
import com.medical.agent.domain.dto.response.TimelineProfilesResponseData;
import com.medical.agent.domain.vo.TimelineProfileSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {
  private final TimelineService timelineService;

  public TimelineController(TimelineService timelineService) {
    this.timelineService = timelineService;
  }

  @GetMapping
  public ApiResponse<TimelineProfilesResponseData> timeline() {
    List<TimelineProfileSummary> profiles = timelineService.listProfiles();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new TimelineProfilesResponseData(profiles));
  }

  @GetMapping("/{profileId}")
  public ApiResponse<TimelineProfileDetailResponseData> profile(@PathVariable("profileId") String profileId) {
    List<TimelineRecordSummary> records = timelineService.listProfileRecords(profileId);
    String diseaseName = timelineService.diseaseNameByProfile(profileId);
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new TimelineProfileDetailResponseData(profileId, diseaseName, records));
  }
}
