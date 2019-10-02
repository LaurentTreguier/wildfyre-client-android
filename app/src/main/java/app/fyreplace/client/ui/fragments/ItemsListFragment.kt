package app.fyreplace.client.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.observe
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.fyreplace.client.R
import app.fyreplace.client.databinding.FragmentItemsListBinding
import app.fyreplace.client.ui.adapters.ItemsAdapter
import app.fyreplace.client.viewmodels.ItemsListFragmentViewModel
import kotlinx.android.synthetic.main.fragment_items_list.*

/**
 * Base class for fragments displaying a list of items.
 */
abstract class ItemsListFragment<I, VM : ItemsListFragmentViewModel<I>, A : ItemsAdapter<I>> :
    FailureHandlingFragment(R.layout.fragment_items_list), ItemsAdapter.OnItemClickedListener<I> {
    abstract override val viewModel: VM
    abstract val itemsAdapter: A
    private var itemsRefreshCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentItemsListBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            loading = viewModel.loading
            hasData = viewModel.hasData
        }

        binding.itemsList.setHasFixedSize(true)
        binding.itemsList.adapter = itemsAdapter.apply {
            onItemClickedListener = this@ItemsListFragment
            viewModel.itemsPagedList.observe(viewLifecycleOwner) {
                submitList(it)
                viewModel.setHasData(it.size > 0)
            }
        }

        binding.refresher.setColorSchemeResources(R.color.colorPrimary)
        binding.refresher.setProgressBackgroundColorSchemeResource(R.color.colorBackground)
        binding.refresher.setOnRefreshListener { refreshItems(false) }
        viewModel.loading.observe(viewLifecycleOwner) { binding.refresher?.isRefreshing = it }
        viewModel.dataSource.observe(viewLifecycleOwner) {
            itemsRefreshCallback = { -> it.invalidate() }

            if (viewModel.popRefresh()) {
                refreshItems(false)
            }
        }

        return binding.root
    }

    override fun onFailure(failure: Throwable) {
        super.onFailure(failure)
        refresher?.isRefreshing = false
    }

    override fun onItemClicked(item: I) = viewModel.pushRefresh()

    fun refreshItems(clear: Boolean) = itemsRefreshCallback?.let {
        if (clear) {
            refresher?.isRefreshing = true
        }

        it()
    }
}
