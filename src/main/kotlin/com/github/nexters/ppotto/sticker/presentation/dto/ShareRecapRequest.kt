package com.github.nexters.ppotto.sticker.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "리캡 공유 요청")
data class ShareRecapRequest(
    @field:Schema(description = "리캡 사진(테마 속 사진)을 공유 링크에 포함할지 여부. false면 서버가 사진 URL을 발급하지 않음", example = "true")
    val includePhotos: Boolean,
)

@Schema(description = "발급된 리캡 공유 정보")
data class ShareRecapResponse(
    @field:Schema(description = "공유 링크 토큰. 공유를 해제하기 전까지 유효하며 다시 공유해도 같은 값이 유지됨", example = "01983f30-0000-7000-8000-000000000000")
    val shareToken: String,

    @field:Schema(description = "이 공유 링크가 리캡 사진을 포함하는지 여부", example = "true")
    val includePhotos: Boolean,
)
