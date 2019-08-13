package com.faltro.perch.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.widget.EditText
import com.faltro.perch.R
import com.faltro.perch.model.Category
import com.faltro.perch.model.Complexity
import com.faltro.perch.model.SortType
import com.faltro.perch.model.Submission
import com.faltro.perch.net.PolyClient
import com.faltro.perch.view.MenuAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.content


class MainActivity : AppCompatActivity() {
    companion object {
        const val FIELD_SUBMISSION: String = "submission"
    }


    private val polyClient: PolyClient = PolyClient()
    private val items: ArrayList<Submission> = arrayListOf()
    private var sortType: SortType = SortType.BEST
    private var category: Category = Category.ALL
    private var maxComplexity: Complexity = Complexity.ANY
    private lateinit var adapter: MenuAdapter

    private lateinit var sortSubMenu: SubMenu
    private lateinit var categoriesSubMenu: SubMenu
    private lateinit var complexitySubMenu: SubMenu

    private var previousTotal = 0
    private var loading = true
    private var visibleThreshold = 4
    private var firstVisibleItem: Int = 0
    private var visibleItemCount: Int = 0
    private var totalItemCount: Int = 0
    private var nextPageToken: String = ""
    private var searchKeywords: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val layoutManager = GridLayoutManager(this, 2)
        recycler_view.layoutManager = layoutManager

        adapter = MenuAdapter(items) {
            val intent = Intent(this, SubmissionActivity::class.java)
            intent.putExtra(FIELD_SUBMISSION, it)
            startActivity(intent)
        }
        recycler_view.adapter = adapter

        swipe_layout.setOnRefreshListener {
            items.clear()
            fetchItems(ignorePageToken = true)
        }

        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                visibleItemCount = recycler_view.childCount
                totalItemCount = layoutManager.itemCount
                firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                if (loading) {
                    if (totalItemCount > previousTotal) {
                        loading = false
                        previousTotal = totalItemCount
                    }
                }
                if (!loading && totalItemCount - visibleItemCount <= firstVisibleItem + visibleThreshold) {
                    loading = true

                    fetchItems()
                }
            }
        })

        // get initial items
        fetchItems()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_actions, menu)
        sortSubMenu = menu.findItem(R.id.menu_sort).subMenu
        categoriesSubMenu = menu.findItem(R.id.menu_categories).subMenu
        complexitySubMenu = menu.findItem(R.id.menu_complexity).subMenu

        val searchItem = menu.findItem(R.id.menu_search)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            val editText = searchView.findViewById<EditText>(android.support.v7.appcompat.R.id.search_src_text)
            editText.hint = "Enter keywords..."

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchKeywords = query ?: ""
                    items.clear()
                    fetchItems(ignorePageToken = true)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return true
                }
            })

            // check when searchView is closed
            // Would prefer to use searchView.onCloseListener, but it doesn't seem to work
            // https://stackoverflow.com/a/24573307
            searchView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(arg0: View) {
                    searchKeywords = ""
                    if (searchView.query.toString() != "") {
                        items.clear()
                        fetchItems(ignorePageToken = true)
                    }
                }

                override fun onViewAttachedToWindow(arg0: View) {
                }
            })
        }

        return super.onCreateOptionsMenu(menu)
    }

    fun updateSortType(menuItem: MenuItem) {
        // update checked status of menu items
        for (i in 0 until sortSubMenu.size()) {
            sortSubMenu.getItem(i).isChecked = false
        }
        menuItem.isChecked = true

        // update sortType param and reload items
        sortType = SortType.getSortTypeByName(menuItem.title.toString())
        items.clear()
        fetchItems(ignorePageToken = true)
    }

    fun updateCategory(menuItem: MenuItem) {
        // update checked status of menu items
        for (i in 0 until categoriesSubMenu.size()) {
            categoriesSubMenu.getItem(i).isChecked = false
        }
        menuItem.isChecked = true

        // update category param and reload items
        category = Category.getCategoryByName(menuItem.title.toString())
        items.clear()
        fetchItems(ignorePageToken = true)
    }

    fun updateComplexity(menuItem: MenuItem) {
        // update checked status of menu items
        for (i in 0 until complexitySubMenu.size()) {
            complexitySubMenu.getItem(i).isChecked = false
        }
        menuItem.isChecked = true

        // update maxComplexity param and reload items
        maxComplexity = Complexity.getComplexityByName(menuItem.title.toString())
        items.clear()
        fetchItems(ignorePageToken = true)
    }

    private fun fetchItems(ignorePageToken: Boolean = false) = CoroutineScope(Dispatchers.Main).launch {
        val params: MutableMap<String, String> = mutableMapOf(
                Pair("orderBy", sortType.param),
                Pair("category", category.param),
                Pair("maxComplexity", maxComplexity.param)
        )
        if (nextPageToken != "" && !ignorePageToken) params["pageToken"] = nextPageToken
        if (searchKeywords != "") params["keywords"] = searchKeywords

        val data = async(Dispatchers.IO) {
            polyClient.request(params)
        }

        val ele: JsonElement = Json.unquoted.parseJson(data.await())
        val assets: JsonArray = ele.jsonObject.getArray("assets")
        nextPageToken = ele.jsonObject["nextPageToken"]?.content ?: ""

        for (asset in assets) {
            val submission = Submission(asset.jsonObject)
            items.add(submission)
        }

        adapter.notifyDataSetChanged()
        swipe_layout.isRefreshing = false
    }
}
