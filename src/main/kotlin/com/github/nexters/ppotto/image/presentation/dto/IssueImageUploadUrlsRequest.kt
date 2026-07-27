package com.github.nexters.ppotto.image.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class IssueImageUploadUrlsRequest(
    @field:NotEmpty
    @field:Size(max = 200)
    val fileNames: List<@NotBlank String>,
)
