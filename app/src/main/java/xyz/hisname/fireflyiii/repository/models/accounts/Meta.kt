package xyz.hisname.fireflyiii.repository.models.accounts

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Meta(
        val pagination: Pagination
)