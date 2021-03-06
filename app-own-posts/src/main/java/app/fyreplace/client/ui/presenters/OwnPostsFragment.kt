package app.fyreplace.client.ui.presenters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import app.fyreplace.client.app.own_posts.R
import app.fyreplace.client.viewmodels.OwnPostsFragmentViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * [androidx.fragment.app.Fragment] listing the user's own posts.
 */
class OwnPostsFragment : PostsListFragment<OwnPostsFragmentViewModel>(true) {
    override val viewModel by viewModel<OwnPostsFragmentViewModel>()
    override val navigator by inject<Navigator> { parametersOf(this) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = super.onCreateView(inflater, container, savedInstanceState)
        .apply { bd.text.setText(R.string.own_posts_empty) }

    interface Navigator : PostsListFragment.Navigator
}
