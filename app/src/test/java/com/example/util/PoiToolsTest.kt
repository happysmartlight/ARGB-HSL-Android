package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.PoiGallery
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Công cụ POI: giá trị kỳ vọng lấy từ chính code desktop (`services/poi_upload.py`,
 * `poi_fleet.py`, `brightness.py`, `poi_polar.py`) để hai app khớp nhau.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PoiToolsTest {

    private fun res(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream("poi/$name")!!.readBytes()

    @Test
    fun deviceGating() {
        assertNull(PoiTools.blockReason("ARGB HSL Controller 2X Pro", 2611103))
        assertNull(PoiTools.blockReason("  argb hsl controller 2x pro ", 2611050))
        assertEquals(PoiTools.Block.PRODUCT, PoiTools.blockReason("ARGB HSL Controller 4X", 2611103))
        assertEquals(PoiTools.Block.PRODUCT, PoiTools.blockReason(null, 2611103))
        assertEquals(PoiTools.Block.FIRMWARE, PoiTools.blockReason("ARGB HSL Controller 2X Pro", 2611049))
        assertEquals(PoiTools.Block.FIRMWARE, PoiTools.blockReason("ARGB HSL Controller 2X Pro", null))
        assertEquals(PoiTools.BMP_LIMIT_PSRAM, PoiTools.deviceBmpLimit(2611103, 8103383))
        assertEquals(PoiTools.POI_MAX_BMP_BYTES, PoiTools.deviceBmpLimit(2611103, 0))
        assertEquals(PoiTools.POI_MAX_BMP_BYTES, PoiTools.deviceBmpLimit(2611000, 8103383))
    }

    @Test
    fun bmpSizesMatchDesktop() {
        assertEquals(listOf(144, 72, 326, 15, 720), listOf(
            PoiTools.poiBmpHeight(72, 480, 240), PoiTools.poiBmpHeight(72, 420, 420), PoiTools.poiBmpHeight(145, 577, 256),
            PoiTools.poiBmpHeight(15, 200, 200), PoiTools.poiBmpHeight(72, 1000, 100)
        ))
        assertEquals(listOf(31158, 15606, 142190), listOf(
            PoiTools.poiBmpSize(72, 480, 240), PoiTools.poiBmpSize(72, 420, 420), PoiTools.poiBmpSize(145, 577, 256)
        ))
        assertEquals(298, PoiTools.discRows(72))
        assertEquals(452, PoiTools.discRows(72, PoiTools.BMP_LIMIT_PSRAM))
        assertEquals(147, PoiTools.discRows(145))
        assertEquals(911, PoiTools.discRows(145, PoiTools.BMP_LIMIT_PSRAM))
        assertEquals(103, PoiTools.maxFittingPixels(480, 240))
        assertEquals(145, PoiTools.maxFittingPixels(200, 2000))
        assertEquals("63 KB", PoiTools.limitLabel(PoiTools.POI_MAX_BMP_BYTES))
    }

    @Test
    fun namesMatchDesktop() {
        assertEquals(listOf("Su_tu_do", "Rong_vang", "ANH_logo-01_png", ""),
            listOf("Sư tử đỏ!", "  Rồng  vàng ", "ẢNH_logo-01.png", "***").map { PoiTools.asciiSlug(it) })
        assertEquals(listOf("logo_khai_truong_v1", "logo_khai_truong_v2", "Logo_2", "logo_3"),
            PoiTools.uniquePresetNames(listOf("logo_khai_truong_v1", "logo_khai_truong_v2", "Logo", "logo"), listOf("LOGO")))
        assertEquals("Rong_vang_rat_d_disc", PoiTools.discStem("Rồng vàng rất dài tên quá"))
        assertTrue(PoiTools.isDiscStem("abc_disc_2"))
        assertFalse(PoiTools.isDiscStem("disc_abc"))
        assertEquals("ID 1-3, 7, 9-10", PoiTools.formatSlots(listOf(1, 2, 3, 7, 9, 10)))
        assertEquals(listOf(0, 50, 100, 100), listOf(1, 51, 101, 200).map { PoiTools.briToPercent(it) })
    }

    @Test
    fun scanRowsMatchDesktop() {
        assertEquals(2000, PoiTools.maxScanRows(2611103, listOf(51 to 72)))   // APA102 (2 dây)
        assertEquals(406, PoiTools.maxScanRows(2611103, listOf(22 to 72)))    // WS2812 (1 dây)
        assertNull(PoiTools.maxScanRows(2611000, listOf(51 to 72)))
        assertEquals(864, PoiTools.scanRowsPerSecond(144, 120, 3))
    }

    @Test
    fun presetPayloadAndSavedCheck() {
        val payload = PoiTools.imagePresetPayload("logo.bmp", 7, "logo", 142, 51, 72, 120, 3, true)
        val seg = payload.getJSONArray("seg").getJSONObject(0)
        assertEquals("/logo.bmp", seg.getString("n"))
        assertEquals(120, seg.getInt("ix")); assertEquals(3, seg.getInt("c3")); assertTrue(seg.getBoolean("o3"))
        assertEquals(0, seg.getInt("start")); assertEquals(72, seg.getInt("stop"))
        assertEquals(7, payload.getInt("psave")); assertTrue(payload.getBoolean("ib")); assertTrue(payload.getBoolean("sb"))
        val saved = JSONObject().put("7", JSONObject("""{"n":"logo","bri":51,"seg":[{"id":0,"fx":142,"n":"/logo.bmp","ix":120,"c3":3,"o3":true,"sx":128}]}"""))
        assertTrue(PoiTools.presetSaved(saved, 7, payload))
        val stale = JSONObject().put("7", JSONObject("""{"n":"logo","seg":[{"fx":142,"n":"/logo.bmp","ix":0,"c3":3,"o3":true}]}"""))
        assertFalse(PoiTools.presetSaved(stale, 7, payload))
        assertFalse(PoiTools.presetSaved(JSONObject(), 7, payload))
    }

    @Test
    fun logoSlotsAndReservedNames() {
        val presets = JSONObject()
            .put("1", JSONObject("""{"n":"a","seg":[{"n":"/a.bmp"}]}"""))
            .put("3", JSONObject("""{"n":"b","seg":[{"n":"/b.bmp"}]}"""))
            .put("2", JSONObject())
        assertEquals(listOf(2, 4, 5), PoiTools.logoSlots(presets, 3, overwrite = false))
        assertEquals(listOf(1, 2), PoiTools.logoSlots(presets, 2, overwrite = true))
        assertEquals(setOf("b"), PoiTools.reservedBmpStems(presets, listOf(1, 2)))
    }

    @Test
    fun identifyPlanMatchesDesktop() {
        val resume = PoiTools.identifyPlan(JSONObject("""{"on":true,"bri":80,"ps":5,"seg":[{"id":0,"fx":142}]}"""))
        assertEquals(8, resume.size)
        assertEquals(
            com.example.data.PresetJson.canonical(JSONObject("""{"tt":0,"seg":[{"id":0,"col":[[255,255,255]],"fx":0}],"on":true,"bri":40}""")),
            com.example.data.PresetJson.canonical(resume[0].first)
        )
        assertEquals(250L, resume[0].second)
        assertEquals(com.example.data.PresetJson.canonical(JSONObject("""{"ps":5}""")), com.example.data.PresetJson.canonical(resume[6].first))
        assertEquals(300L, resume[6].second)
        assertEquals(com.example.data.PresetJson.canonical(JSONObject("""{"on":true,"bri":80}""")), com.example.data.PresetJson.canonical(resume[7].first))
        val restore = PoiTools.identifyPlan(JSONObject("""{"on":false,"bri":30,"ps":-1,"pl":-1,"seg":[{"id":0,"fx":9,"col":[[1,2,3]]}]}"""))
        assertEquals(7, restore.size)
        assertEquals(com.example.data.PresetJson.canonical(JSONObject("""{"on":false,"bri":30,"seg":[{"id":0,"fx":9,"col":[[1,2,3]]}]}""")),
            com.example.data.PresetJson.canonical(restore[6].first))
    }

    @Test
    fun temperatureFromRealInfo() {
        val info = JSONObject("""{"sensor":{"Nhi&#x1EC7;t &#x111;&#x1ED9; ARGB HSL":[56.9,"°C"]},"u":{"Autosave":["<button>"]}}""")
        assertEquals(56.9, PoiTools.tempCelsius(info)!!, 1e-9)
        assertNull(PoiTools.tempCelsius(JSONObject()))
    }

    // ── Xử lý ảnh: so với ảnh desktop vẽ ra từ cùng file nguồn ──────────

    private fun decode(name: String): Bitmap = BitmapFactory.decodeByteArray(res(name), 0, res(name).size)

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    @Test
    fun discPolarMatchesDesktopPixelForPixel() {
        val src = decode("hsl-mang-nhen-neon.webp")
        val expected = decode("expected_disc_72.png")
        val out = PoiImage.toPolar(src, 72, PoiTools.discRows(72), 15)
        assertEquals(expected.width, out.width)
        assertEquals(expected.height, out.height)
        val a = pixels(out)
        val e = pixels(expected)
        var maxDiff = 0
        var diffCount = 0
        for (i in a.indices) {
            for (sh in intArrayOf(16, 8, 0)) {
                val d = abs(((a[i] shr sh) and 0xFF) - ((e[i] shr sh) and 0xFF))
                if (d > maxDiff) maxDiff = d
                if (d > 2) diffCount++
            }
        }
        // Giải mã WebP (Skia vs libwebp của PIL) có thể lệch ±1–2; còn lại phải trùng.
        assertTrue("lệch tối đa $maxDiff", maxDiff <= 3)
        assertTrue("$diffCount kênh lệch > 2", diffCount < a.size * 3 / 200)
    }

    @Test
    fun bmpEncodingIsByteIdenticalToPil() {
        // Dùng chính ảnh desktop (PNG) làm dữ liệu điểm ảnh → BMP phải trùng từng byte với PIL.
        val expectedBmp = res("expected_disc_72.bmp")
        val bmp = PoiImage.encodeBmp24(decode("expected_disc_72.png"))
        assertArrayEquals(expectedBmp, bmp)
        assertTrue(PoiTools.fits(bmp.size, PoiTools.POI_MAX_BMP_BYTES))
    }

    @Test
    fun flatResizeMatchesDesktopShape() {
        val src = decode("FADE03-200px.webp")
        val expected = decode("expected_flat_72.png")
        val out = PoiImage.rotateResizeWidth(src, 72)
        assertEquals(expected.width, out.width)
        assertEquals(expected.height, out.height)
        // Cách lọc khác PIL nên không trùng từng điểm, nhưng màu trung bình phải gần như nhau.
        val a = pixels(out); val e = pixels(expected)
        for (sh in intArrayOf(16, 8, 0)) {
            val ma = a.map { (it shr sh) and 0xFF }.average()
            val me = e.map { (it shr sh) and 0xFF }.average()
            assertTrue("kênh $sh: $ma vs $me", abs(ma - me) < 4.0)
        }
    }

    @Test
    fun spinRendererDrawsRingWithHole() {
        val src = PoiImage.previewSource(decode("expected_disc_72.png"))
        val r = PoiSpinRenderer(src, 100, SpinParams.disc(15))
        val out = IntArray(100 * 100)
        r.render(null, out)
        val black = 0xFF000000.toInt()
        assertEquals(black, out[50 * 100 + 50])          // tâm trống
        assertEquals(black, out[0])                       // ngoài vòng
        assertTrue(out.count { it != black } > 2000)      // vòng có hình
        val dim = IntArray(100 * 100)
        r.render(1.0, dim)
        assertTrue(dim.count { it != black } <= out.count { it != black })
    }

    @Test
    fun galleryCatalogParsesAndSearchesWithoutDiacritics() {
        val doc = JSONObject("""{"groups":[{"id":"animal","name":"Động vật"}],
            "items":[{"file":"src/su-tu.webp","thumb":"thumb/su-tu.webp","title":"Sư tử lửa","group":"animal","shape":"disc","featured":true},
                     {"file":"src/x.webp","thumb":"thumb/x.webp","title":"Cá chép","group":"animal","shape":"flat"},
                     {"file":"src/bad.webp","thumb":"t","title":"Lỗi","group":"none"}]}""")
        val g = PoiGallery.parse(doc)
        assertEquals(2, g.items.size)
        assertEquals("Sư tử lửa", g.search("su tu").single().title)
        assertEquals(2, g.search("dong vat").size)              // khớp theo tên nhóm
        assertEquals(PoiTools.Shape.DISC, g.byKey("su-tu")!!.shape)
        assertEquals(listOf("x", "su-tu"), g.quickPicks(favorites = listOf("x"), recents = emptyList()).map { it.key })
        assertEquals(listOf("b", "a"), PoiGallery.pushRecent(listOf("a", "b"), "b"))
    }
}
