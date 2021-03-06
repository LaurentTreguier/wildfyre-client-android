package app.fyreplace.client.viewmodels

import androidx.lifecycle.*
import app.fyreplace.client.data.models.Comment
import app.fyreplace.client.data.models.Flag
import app.fyreplace.client.data.models.ImageData
import app.fyreplace.client.data.models.Post
import app.fyreplace.client.data.repositories.AreaRepository
import app.fyreplace.client.data.repositories.CommentRepository
import app.fyreplace.client.data.repositories.PostRepository
import app.fyreplace.client.ui.toMarkdown
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class PostFragmentViewModel(
    private val areaRepository: AreaRepository,
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository
) : ViewModel() {
    var selfId = Long.MIN_VALUE
    var postAreaName = areaRepository.preferredAreaName
        private set
    var postId = -1L
        private set
    protected val mHasContent = MutableLiveData(true)
    protected val mAllowSpread = MutableLiveData(false)
    private val mPost = MutableLiveData<Post?>(null)
    private val mIsOwnPost = MediatorLiveData<Boolean>()
    private val mSubscribed = MediatorLiveData<Boolean>()
    private val mMarkdownContent = MediatorLiveData<String>()
    private val mCommentsVisible = MutableLiveData<Boolean>()
    private val mCommentsDividerVisible = MutableLiveData<Boolean>()
    private val mComments = MediatorLiveData<List<Comment>>()
    private val commentsData = mutableListOf<Comment>()
    private val mNewCommentImage = MutableLiveData<ImageData?>()
    private val mCanSendNewComment = MediatorLiveData<Boolean>()

    val hasContent: LiveData<Boolean> = mHasContent.distinctUntilChanged()
    val allowSpread: LiveData<Boolean> = mAllowSpread.distinctUntilChanged()
    val post: LiveData<Post?> = mPost
    val isOwnPost: LiveData<Boolean> = mIsOwnPost.distinctUntilChanged()
    val contentLoaded: LiveData<Boolean> = post.map { it != null }
    val authorId: LiveData<Long> = post.map { it?.author?.user ?: -1 }
    val subscribed: LiveData<Boolean> = mSubscribed.distinctUntilChanged()
    val markdownContent: LiveData<String> = mMarkdownContent
    val commentSheetState: LiveData<Int> =
        mCommentsVisible.map { if (it) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED }
            .distinctUntilChanged()
    val commentsDividerVisible: LiveData<Boolean> = mCommentsDividerVisible
    val comments: LiveData<List<Comment>> = mComments
    val commentCount: LiveData<Int> = comments.map { it.size }.distinctUntilChanged()
    val newCommentData = MutableLiveData<String>()
    val newCommentImage: LiveData<ImageData?> = mNewCommentImage
    val canSendNewComment: LiveData<Boolean> = mCanSendNewComment

    init {
        mIsOwnPost.addSource(authorId) { mIsOwnPost.postValue(it == selfId) }
        mSubscribed.addSource(post) { mSubscribed.postValue(it?.subscribed ?: false) }
        mMarkdownContent.addSource(post) {
            viewModelScope.launch(Dispatchers.Default) {
                mMarkdownContent.postValue(it?.toMarkdown().orEmpty())
            }
        }
        mComments.addSource(post) {
            commentsData.clear()
            it?.run { commentsData.addAll(comments) }
            mComments.postValue(commentsData)
        }
        mCanSendNewComment.addSource(post) { updateCanSendNewComment() }
        mCanSendNewComment.addSource(newCommentData) { updateCanSendNewComment() }
    }

    suspend fun setPostData(areaName: String?, id: Long) {
        setPost(postRepository.getPost(areaName, id))

        if (areaName != null) {
            postAreaName = areaName
        }
    }

    fun setPost(post: Post?) {
        val newPostId = post?.id ?: -1

        if (newPostId != postId) {
            postAreaName = areaRepository.preferredAreaName
            postId = newPostId
            mPost.postValue(post)
            resetNewComment()
        }
    }

    fun setIsOwnPost() = mIsOwnPost.postValue(true)

    suspend fun getFlagChoices() = postRepository.getFlagChoices()
        .sortedWith { f1, f2 ->
            when {
                f1.key == null -> 1
                f2.key == null -> -1
                f1.key!! < f2.key!! -> -1
                f1.key!! > f2.key!! -> 1
                else -> 0
            }
        }

    suspend fun changeSubscription() = mSubscribed.postValue(
        postRepository.setSubscription(
            postAreaName,
            postId,
            !(subscribed.value ?: false)
        ).subscribed
    )

    suspend fun deletePost() = postRepository.deletePost(postAreaName, postId)

    suspend fun flag(commentId: Long?, key: Long?, comment: String?) =
        postRepository.flag(postAreaName, postId, commentId, Flag(key, comment))

    fun setCommentsVisible(visible: Boolean) = mCommentsVisible.postValue(visible)

    fun setCommentsDividerVisibility(visibility: Int) =
        mCommentsDividerVisible.postValue(visibility == BottomSheetBehavior.STATE_DRAGGING)

    fun setCommentImage(image: ImageData) = mNewCommentImage.postValue(image)

    fun resetCommentImage() = mNewCommentImage.postValue(null)

    suspend fun sendNewComment() = newCommentData.value?.let { commentData ->
        if (postId != -1L) {
            try {
                mCanSendNewComment.postValue(false)
                commentsData.add(
                    commentRepository.sendComment(
                        postAreaName,
                        postId,
                        commentData,
                        newCommentImage.value
                    )
                )
            } catch (t: Throwable) {
                mCanSendNewComment.postValue(commentData.isNotBlank())
                throw t
            }

            mComments.postValue(commentsData)
            mSubscribed.postValue(true)
            resetNewComment()
        }
    }

    suspend fun deleteComment(position: Int, comment: Comment) {
        if (postId != -1L) {
            commentRepository.deleteComment(postAreaName, postId, comment.id)
            commentsData.removeAt(position)
            mComments.postValue(commentsData)
        }
    }

    private fun updateCanSendNewComment() =
        mCanSendNewComment.postValue(post.value != null && newCommentData.value?.isNotBlank() == true)

    private fun resetNewComment() {
        newCommentData.postValue("")
        resetCommentImage()
    }
}
