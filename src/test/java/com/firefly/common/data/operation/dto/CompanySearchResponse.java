package com.firefly.common.data.operation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for company search operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Company search result")
public class CompanySearchResponse {

    @Schema(
        description = "Provider's internal ID",
        example = "PROV-12345",
        required = true
    )
    private String providerId;

    @Schema(
        description = "Company name",
        example = "ACME CORPORATION",
        required = true
    )
    private String companyName;

    @Schema(
        description = "Tax ID",
        example = "TAX-12345678"
    )
    private String taxId;

    @Schema(
        description = "Match confidence score",
        example = "0.95",
        minimum = "0",
        maximum = "1"
    )
    private Double confidence;
}

