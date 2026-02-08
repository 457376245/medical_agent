package com.medical.agent.infrastructure.persistence.mapper;

public interface ParseArtifactsMapper {
  int insertAsset(Object asset);

  int insertParseJob(Object parseJob);

  int insertStructuredResult(Object structuredResult);
}
