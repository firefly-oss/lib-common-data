package com.firefly.common.data.operation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for company search operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to search for a company")
public class CompanySearchRequest {

    @Schema(
        description = "Company name to search for",
        example = "Acme Corp",
        required = true
    )
    private String companyName;

    @Schema(
        description = "Tax ID to search for",
        example = "TAX-12345678",
        pattern = "^TAX-[0-9]{8}$"
    )
    private String taxId;

    @Schema(
        description = "Match confidence threshold",
        example = "0.8",
        minimum = "0",
        maximum = "1"
    )
    private Double minConfidence;
}

