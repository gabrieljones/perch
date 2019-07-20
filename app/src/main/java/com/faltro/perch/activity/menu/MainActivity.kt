package com.faltro.perch.activity.menu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.faltro.perch.BuildConfig
import com.faltro.perch.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.net.URL


class MainActivity : AppCompatActivity() {
    private val items: ArrayList<String> = arrayListOf()
    private lateinit var adapter: MenuAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = MenuAdapter(items)
        recycler_view.adapter = adapter

        swipe_layout.setOnRefreshListener {
            fetchItems()
        }
        swipe_layout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light)
    }

    private fun fetchItems() = CoroutineScope(Dispatchers.Main).launch {
        val data = async(Dispatchers.IO) {
            Thread.sleep(2000)
            URL("https://poly.googleapis.com/v1/assets?key=${BuildConfig.PolyAPIKey}").readText()
        }

        val ele: JsonElement = Json.unquoted.parseJson(data.await())
        val assets: JsonArray = ele.jsonObject.getArray("assets")

        for (asset in assets) {
            val text: String = asset.jsonObject["displayName"].toString()
            items.add(text)
        }

        adapter.notifyDataSetChanged()
        swipe_layout.isRefreshing = false
    }
}
