package net.wildfyre.client.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.fragment_items_list.*
import net.wildfyre.client.R
import net.wildfyre.client.data.Failure
import net.wildfyre.client.databinding.FragmentItemsListBinding
import net.wildfyre.client.ui.adapters.ItemsAdapter
import net.wildfyre.client.viewmodels.ItemsListViewModel

/**
 * Base class for fragments displaying a list of items.
 */
abstract class ItemsListFragment<I, VM : ItemsListViewModel<I>, A : ItemsAdapter<I>> :
    FailureHandlingFragment(R.layout.fragment_items_list), RecyclerView.OnChildAttachStateChangeListener,
    ItemsAdapter.OnItemClickedListener<I> {
    protected var onRefreshListener: SwipeRefreshLayout.OnRefreshListener? = null
    abstract val viewModel: VM

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = FragmentItemsListBinding.inflate(inflater, container, false).run {
            lifecycleOwner = viewLifecycleOwner
            hasData = viewModel.hasData
            return@run root
        }

        val itemsList = root.findViewById<RecyclerView>(R.id.items_list)
        val swipeRefresh = root.findViewById<SwipeRefreshLayout>(R.id.refresher)

        itemsList.setHasFixedSize(true)
        itemsList.adapter = getItemsAdapter().apply {
            onItemClickedListener = this@ItemsListFragment
            viewModel.loading.observe(viewLifecycleOwner, Observer { swipeRefresh.isRefreshing = it })
            viewModel.itemsPagedList.observe(viewLifecycleOwner, Observer { it?.run { submitList(it) } })
        }

        itemsList.addOnChildAttachStateChangeListener(this)
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.background)
        viewModel.dataSource.observe(viewLifecycleOwner, Observer {
            onRefreshListener = SwipeRefreshLayout.OnRefreshListener(it::invalidate)
            swipeRefresh.setOnRefreshListener(onRefreshListener)
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        items_list.removeOnChildAttachStateChangeListener(this)
    }

    override fun onFailure(failure: Failure) {
        super.onFailure(failure)
        refresher.isRefreshing = false
    }

    override fun onChildViewAttachedToWindow(view: View) = viewModel.setHasData(items_list.childCount > 0)

    override fun onChildViewDetachedFromWindow(view: View) = viewModel.setHasData(items_list.childCount > 0)

    abstract fun getItemsAdapter(): A
}