from pydantic import BaseModel, Field


class SourceEvidence(BaseModel):
    source_file: str
    page: int | None = None
    snippet: str | None = None


class StructuredField(BaseModel):
    name: str
    value: str
    confidence: float = Field(ge=0, le=1)
    evidence: SourceEvidence | None = None


class StructuredResultV1(BaseModel):
    schema_version: str = "v1"
    fields: list[StructuredField]
