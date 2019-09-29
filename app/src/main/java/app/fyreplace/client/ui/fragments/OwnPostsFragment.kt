package app.fyreplace.client.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import app.fyreplace.client.NavigationMainDirections.Companion.actionGlobalFragmentPost
import app.fyreplace.client.R
import app.fyreplace.client.data.models.Post
import app.fyreplace.client.ui.adapters.PostsAdapter
import app.fyreplace.client.viewmodels.OwnPostsFragmentViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * [androidx.fragment.app.Fragment] listing the user's own posts.
 */
class OwnPostsFragment : PostsFragment<OwnPostsFragmentViewModel>(true) {
    override val viewModel by viewModel<OwnPostsFragmentViewModel>()
    override val itemsAdapter = PostsAdapter(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = super.onCreateView(inflater, container, savedInstanceState)
        .apply { bd.text.setText(R.string.own_posts_empty) }

    override fun onItemClicked(item: Post) {
        super.onItemClicked(item)
        findNavController().navigate(
            actionGlobalFragmentPost(
                post = item,
                ownPost = true
            )
        )
    }
}
