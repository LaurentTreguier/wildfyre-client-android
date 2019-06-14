package net.wildfyre.client.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.wildfyre.client.data.SingleLiveEvent
import net.wildfyre.client.data.models.Comment
import net.wildfyre.client.data.models.Post
import net.wildfyre.client.data.repositories.AreaRepository
import net.wildfyre.client.data.repositories.CommentRepository
import net.wildfyre.client.data.repositories.PostRepository
import net.wildfyre.client.ui.prepareForMarkdown

open class PostFragmentViewModel(application: Application) : FailureHandlingViewModel(application) {
    var postAreaName: String = AreaRepository.preferredAreaName
        protected set
    var postId: Long = -1
        protected set
    protected val mHasContent = MutableLiveData<Boolean>()
    private val mPost = MutableLiveData<Post>()
    private val mSubscribed = MediatorLiveData<Boolean>()
    private val mMarkdownContent = MediatorLiveData<String>()
    private val mCommentAddedEvent = SingleLiveEvent<Comment>()
    private val mCommentRemovedEvent = SingleLiveEvent<Int>()
    private val mCommentCount = MediatorLiveData<Int>()

    val hasContent: LiveData<Boolean> = mHasContent
    val post: LiveData<Post?> = mPost
    val contentLoaded: LiveData<Boolean> = Transformations.map(post) { it != null }
    val authorId: LiveData<Long> = Transformations.map(post) { it?.author?.user ?: -1 }
    val comments: LiveData<List<Comment>> = Transformations.map(post) { it?.comments ?: emptyList() }
    val subscribed: LiveData<Boolean> = mSubscribed
    val markdownContent: LiveData<String> = mMarkdownContent
    val commentAddedEvent: LiveData<Comment> = mCommentAddedEvent
    val commentRemovedEvent: LiveData<Int> = mCommentRemovedEvent
    val commentCount: LiveData<Int> = mCommentCount
    val newCommentData = MutableLiveData<String>()

    init {
        mHasContent.value = true
        mSubscribed.addSource(post) { mSubscribed.postValue(it?.subscribed ?: false) }
        mMarkdownContent.addSource(post) {
            launchCatching(Dispatchers.Default) {
                val markdownContent = StringBuilder()
                it?.image?.run { markdownContent.append("![]($this)\n\n") }
                it?.text?.run {
                    markdownContent.append(it.additionalImages
                        ?.let { images -> prepareForMarkdown(images) } ?: this)
                }
                mMarkdownContent.postValue(markdownContent.toString())
            }
        }

        mCommentCount.addSource(comments) { mCommentCount.postValue(it.size) }
        mCommentCount.addSource(commentAddedEvent) { mCommentCount.postValue(mCommentCount.value!! + 1) }
        mCommentCount.addSource(commentRemovedEvent) { mCommentCount.postValue(mCommentCount.value!! - 1) }
        newCommentData.value = ""
    }

    fun setPostDataAsync(areaName: String, id: Long) = launchCatching {
        val newPost = if (id == -1L) null else withContext(Dispatchers.IO) { PostRepository.getPost(areaName, id) }
        setPost(newPost)
        postAreaName = areaName
    }

    fun setPost(post: Post?) {
        postAreaName = AreaRepository.preferredAreaName
        postId = post?.id ?: -1
        mPost.postValue(post)
    }

    fun changeSubscriptionAsync() = launchCatching(Dispatchers.IO) {
        mSubscribed.postValue(
            PostRepository.setSubscription(
                postAreaName,
                postId,
                !(subscribed.value ?: false)
            ).subscribed
        )
    }

    fun sendNewCommentAsync() = launchCatching {
        if (newCommentData.value != null && postId != -1L) {
            mCommentAddedEvent.postValue(
                withContext(Dispatchers.IO) {
                    CommentRepository.sendComment(
                        postAreaName,
                        postId,
                        newCommentData.value!!
                    )
                }
            )
            newCommentData.postValue("")
        }
    }

    fun deleteCommentAsync(position: Int, comment: Comment) = launchCatching {
        if (postId != -1L) {
            withContext(Dispatchers.IO) { CommentRepository.deleteComment(postAreaName, postId, comment.id) }
            mCommentRemovedEvent.postValue(position)
        }
    }
}
