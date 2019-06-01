package net.wildfyre.client.data.repositories

import net.wildfyre.client.data.CommentText
import net.wildfyre.client.data.Services
import net.wildfyre.client.data.await

object CommentRepository {
    suspend fun sendComment(areaName: String?, postId: Long, comment: String) =
        Services.webService.postComment(
            AuthRepository.authToken.value!!,
            areaName ?: AreaRepository.preferredAreaName.value.orEmpty(),
            postId,
            CommentText(comment)
        ).await()

    suspend fun deleteComment(areaName: String?, postId: Long, commentId: Long) =
        Services.webService.deleteComment(
            AuthRepository.authToken.value!!,
            areaName ?: AreaRepository.preferredAreaName.value.orEmpty(),
            postId,
            commentId
        ).await()
}
