package com.example.data

import android.content.Context
import com.example.util.PoiTools
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thư viện ảnh mẫu POI (cùng bộ với desktop `assets/poi_gallery/`), đóng gói trong
 * `assets/poi_gallery/` bằng `tools/build_poi_gallery.py`: ảnh nguồn WebP thu nhỏ + thumbnail vẽ
 * sẵn bằng đúng code xem trước của desktop (`render_thumb`). Mỗi mẫu ghi sẵn kiểu hình hợp với
 * ảnh — sai kiểu là ảnh vỡ thành các mảnh.
 */
data class PoiGalleryItem(
    val file: String,
    val thumb: String,
    val title: String,
    val group: String,
    val shape: PoiTools.Shape,
    val featured: Boolean
) {
    /** Khoá ổn định (lưu yêu thích / gần đây). */
    val key: String get() = PoiTools.fileStem(file)
}

data class PoiGalleryGroup(val id: String, val name: String)

class PoiGallery(val groups: List<PoiGalleryGroup>, val items: List<PoiGalleryItem>) {
    private val byKey = items.associateBy { it.key }

    val isEmpty: Boolean get() = items.isEmpty()

    fun byKey(key: String): PoiGalleryItem? = byKey[key]

    fun groupName(id: String): String = groups.firstOrNull { it.id == id }?.name ?: id

    /** Mọi từ trong [query] (không dấu) phải có trong tên / tên file; khớp tên đứng trước khớp nhóm. */
    fun search(query: String, group: String? = null): List<PoiGalleryItem> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        val byName = ArrayList<PoiGalleryItem>()
        val byGroup = ArrayList<PoiGalleryItem>()
        for (item in items) {
            if (group != null && item.group != group) continue
            val name = fold("${item.title} ${item.key}")
            if (words.all { it in name }) byName += item
            else if (words.all { it in "$name ${fold(groupName(item.group))}" }) byGroup += item
        }
        return byName + byGroup
    }

    /** Hàng chọn nhanh: yêu thích trước, rồi gần đây, chưa đủ thì thêm mẫu gợi ý. */
    fun quickPicks(favorites: List<String>, recents: List<String>, limit: Int = QUICK_LIMIT): List<PoiGalleryItem> {
        val out = ArrayList<PoiGalleryItem>()
        for (k in favorites + recents + items.filter { it.featured }.map { it.key }) {
            val item = byKey[k] ?: continue
            if (item !in out) out += item
            if (out.size == limit) break
        }
        return out
    }

    companion object {
        const val DIR = "poi_gallery"
        const val QUICK_LIMIT = 6
        const val RECENT_LIMIT = 12

        /** Bỏ dấu + chữ thường để gõ "su tu" vẫn ra "Sư tử". */
        fun fold(text: String): String = PoiTools.asciiSlug(text).replace('_', ' ').replace('-', ' ').lowercase()

        fun load(context: Context): PoiGallery = try {
            val doc = context.assets.open("$DIR/gallery.json").use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            parse(doc)
        } catch (e: Exception) {
            android.util.Log.w("PoiGallery", "Không đọc được thư viện mẫu", e)
            PoiGallery(emptyList(), emptyList())
        }

        fun parse(doc: JSONObject): PoiGallery {
            val groups = (doc.optJSONArray("groups") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .map { PoiGalleryGroup(it.optString("id"), it.optString("name")) }
            }
            val known = groups.map { it.id }.toSet()
            val items = (doc.optJSONArray("items") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .filter { it.optString("group") in known && it.optString("file").isNotBlank() }
                    .map {
                        PoiGalleryItem(
                            file = it.optString("file"),
                            thumb = it.optString("thumb"),
                            title = it.optString("title"),
                            group = it.optString("group"),
                            shape = PoiTools.Shape.of(it.optString("shape")),
                            featured = it.optBoolean("featured", false)
                        )
                    }
            }
            return PoiGallery(groups, items)
        }

        fun pushRecent(recents: List<String>, key: String, limit: Int = RECENT_LIMIT): List<String> =
            (listOf(key) + recents.filter { it != key }).take(limit)

        fun toggleFavorite(favorites: List<String>, key: String): List<String> =
            if (key in favorites) favorites - key else favorites + key
    }
}
