package com.medical.agent.application.service;

import com.medical.agent.application.PersistenceService;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.ParseRequestEvent;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ParseJobService {
  private final PersistenceService persistenceService;
  private final ParseRequestPublisher parseRequestPublisher;

  public ParseJobService(PersistenceService persistenceService, ParseRequestPublisher parseRequestPublisher) {
    this.persistenceService = persistenceService;
    this.parseRequestPublisher = parseRequestPublisher;
  }

  public ParseJobResponseData create(CreateParseJobRequest request, String idempotencyKey) {
    UUID recordId = UUID.fromString(request.recordId());
    UUID jobId = persistenceService.createOrReuseParseJob(recordId, idempotencyKey);
    List<String> rawAssetIds = request.assetIds() == null ? List.of() : request.assetIds();
    List<UUID> assetIds = rawAssetIds.stream().map(UUID::fromString).toList();
    persistenceService.bindParseJobAssets(jobId, assetIds);
    List<AssetRef> assetRefs = persistenceService.listAssetRefs(assetIds);
    ParseJobContext context = persistenceService.parseJobContext(jobId);

    parseRequestPublisher.publish(new ParseRequestEvent(
        jobId.toString(),
        context.tenantId(),
        context.userId(),
        assetRefs,
        UUID.randomUUID().toString().replace("-", ""),
        "v1",
        idempotencyKey));

    return new ParseJobResponseData(jobId.toString(), "QUEUED");
  }
}
