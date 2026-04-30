package com.medical.agent.infrastructure.mq;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.service.ParseJobService;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.exception.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParseResultConsumerTest {

  @Test
  void ignoresParseResultForUnknownJob() {
    ParseJobService parseJobService = Mockito.mock(ParseJobService.class);
    RecordService recordService = Mockito.mock(RecordService.class);
    GenerateRequestPublisher generateRequestPublisher = Mockito.mock(GenerateRequestPublisher.class);
    ParseResultConsumer consumer = new ParseResultConsumer(
        new ObjectMapper(),
        parseJobService,
        recordService,
        generateRequestPublisher);
    String jobId = UUID.randomUUID().toString();

    when(parseJobService.applyParseResult(any(), any(), any(), anyDouble(), isNull()))
        .thenThrow(new ResourceNotFoundException("parse job not found"));

    assertDoesNotThrow(() -> consumer.consume(
        "{" +
            "\"jobId\":\"" + jobId + "\"," +
            "\"status\":\"FAILED\"," +
            "\"structuredResult\":{}," +
            "\"confidence\":0.0," +
            "\"errors\":[]" +
            "}"));

    verify(recordService, never()).applyAutoClassification(any(), any());
    verify(generateRequestPublisher, never()).publish(any());
  }
}
