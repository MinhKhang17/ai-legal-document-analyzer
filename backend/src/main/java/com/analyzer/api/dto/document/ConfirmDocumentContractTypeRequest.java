package com.analyzer.api.dto.document;

import jakarta.validation.constraints.NotBlank;

public record ConfirmDocumentContractTypeRequest(@NotBlank String contractType) {}
