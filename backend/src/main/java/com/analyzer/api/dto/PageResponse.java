package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard paginated response wrapper")
public class PageResponse<T> {

    @Schema(description = "List of items in the current page")
    private List<T> items;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Number of items per page", example = "10")
    private int size;

    @Schema(description = "Total number of items across all pages", example = "42")
    private long totalItems;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;
}
