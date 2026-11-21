package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** File /show.jsonl — cùng các ca với desktop `tests/test_show_file.py` (Tool ARGB HSL v3.17.11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowFileTest {
    private val arm = ShowFile.Segment("Giáp tay", 40, 120, key = "arm")
    private val head = ShowFile.Segment("Giáp đầu", 0, 40, key = "head", color = "#5fd6c4")

    private fun lines(text: String) = text.trim().split("\n").map { JSONObject(it) }

    private fun clips() = listOf(
        ShowFile.Clip(null, 0.0, 4.0, 0.0, "FX9 Rainbow",
            JSONObject("""{"id":0,"start":0,"stop":300,"fx":9,"col":[[255,160,0]],"on":true}""")),
        ShowFile.Clip("arm", 5.0, 4.0, 0.3, "FX28 Chase", JSONObject("""{"fx":28,"pal":5,"sx":200}"""))
    )

    @Test fun headerAndClipLines() {
        val built = ShowFile.buildShow("Show A", listOf(head, arm), clips(), 14.0, loop = true)
        val (h, c1, c2) = lines(built.text)
        assertEquals(1, h.getInt("v"))
        assertEquals(14000, h.getInt("dur"))
        assertTrue(h.getBoolean("loop"))
        val segs = h.getJSONArray("segs")
        assertEquals("Giáp đầu", segs.getJSONObject(0).getString("n"))
        assertEquals("head", segs.getJSONObject(0).getString("k"))
        assertEquals("#5fd6c4", segs.getJSONObject(0).getString("c"))
        assertFalse(segs.getJSONObject(0).has("startY"))           // dải LED: không ghi hàng
        assertEquals(-1, c1.getInt("s"))
        val look = c1.getJSONObject("seg")
        assertEquals(9, look.getInt("fx"))
        assertFalse(look.has("start") || look.has("id") || look.has("on"))   // clip không đổi bố cục
        assertEquals(1, c2.getInt("s"))
        assertEquals(5000, c2.getInt("t"))
        assertEquals(300, c2.getInt("tr"))
        assertTrue(built.warnings.isEmpty())
        assertEquals(2, ShowFile.clipCount(built.text))
    }

    @Test fun clipWithoutLookOrDeletedSegmentIsSkipped() {
        val built = ShowFile.buildShow("x", listOf(arm), listOf(
            ShowFile.Clip(null, 0.0, 1.0, 0.0, "rỗng", null),
            ShowFile.Clip("gone", 0.0, 1.0, 0.0, "mất đoạn", JSONObject("""{"fx":1}""")),
            ShowFile.Clip(null, 2.0, 0.0, 0.0, "dài 0", JSONObject("""{"fx":1}"""))
        ), 3.0, loop = false)
        assertEquals(0, ShowFile.clipCount(built.text))
        assertEquals(2, built.warnings.size)
    }

    @Test fun roundTripWithMarksAndMatrixAndLedOrderSegments() {
        val rect = ShowFile.Segment("Nền", 0, 9, 0, 20, key = "bg")
        val lo = ShowFile.Segment("Tay trái", 14, 21, 3, 9, key = "la", ledOrder = true)
        val text = ShowFile.buildShow("Hình", listOf(rect, ShowFile.Segment.fromHeader(lo.toHeader())!!), listOf(
            ShowFile.Clip("la", 1.25, 2.0, 0.1, "Đỏ", JSONObject("""{"fx":0,"col":[[255,0,0]]}""")),
            ShowFile.Clip(null, 0.0, 1.0, 0.0, "Cả mạch", JSONObject("""{"fx":9}"""))
        ), 5.0, loop = false, marks = listOf(1.0, 0.5, 1.0, -2.0)).text
        val h = lines(text)[0]
        assertEquals(true, h.getJSONArray("segs").getJSONObject(1).getBoolean("lo"))
        assertFalse(h.getJSONArray("segs").getJSONObject(1).has("startY"))
        assertEquals(20, h.getJSONArray("segs").getJSONObject(0).getInt("stopY"))
        assertEquals(listOf(500L, 1000L), (0 until h.getJSONArray("marks").length()).map { h.getJSONArray("marks").getLong(it) })
        val p = ShowFile.parseShow(text)!!
        assertEquals("Hình", p.name)
        assertEquals(5.0, p.duration, 1e-9)
        assertEquals(listOf(0.5, 1.0), p.marks)
        assertEquals(listOf("bg", "la"), p.segments.map { it.key })
        assertTrue(p.segments[1].ledOrder)
        assertEquals(0 to 1, p.segments[1].startY to p.segments[1].stopY)   // đoạn Thứ tự LED luôn 1 hàng
        assertEquals(20, p.segments[0].stopY)
        assertEquals(listOf(null, "la"), p.clips.map { it.segment })
        assertEquals(1.25, p.clips[1].start, 1e-9)
        assertEquals(0.1, p.clips[1].transition, 1e-9)
    }

    @Test fun notAShow() {
        assertNull(ShowFile.parseShow(""))
        assertNull(ShowFile.parseShow("{\"v\":9}\n"))
        assertNull(ShowFile.parseShow("không phải json"))
    }

    @Test fun segmentFromTimelineFileDict() {
        val s = ShowFile.Segment.fromDict(JSONObject("""{"key":"k1","name":" Thân ","start":28,"stop":48,"led_order":true}"""))!!
        assertEquals("Thân", s.name)
        assertTrue(s.ledOrder)
        val r = ShowFile.Segment.fromDict(JSONObject("""{"key":"k2","name":"","start":0,"stop":9,"start_y":0,"stop_y":20}"""))!!
        assertEquals("Phân đoạn", r.name)
        assertEquals(20, r.stopY)
        assertNull(ShowFile.Segment.fromDict(JSONObject("""{"start":5,"stop":5}""")))
    }

    @Test fun playerVersionAndPresets() {
        assertEquals(7, ShowFile.playerVersion(JSONObject("""{"show":{"v":7,"clips":3}}""")))
        assertEquals(0, ShowFile.playerVersion(JSONObject("""{"leds":{}}""")))
        val play = ShowFile.playPreset("Timecode 249")
        assertTrue(ShowFile.isPlayPreset(play))
        assertEquals(0, play.getJSONObject("show").getInt("play"))
        assertFalse(ShowFile.isPlayPreset(JSONObject("""{"playlist":{"ps":[60]}}""")))
        val off = ShowFile.offPreset()
        assertEquals(ShowFile.OFF_PRESET_SEGMENTS, off.getJSONArray("seg").length())
        assertEquals(false, off.getJSONObject("show").getBoolean("restore"))
        assertEquals("""{"show":{"stop":true,"off":true}}""", PresetJson.compact(ShowFile.stopOffCommand()))
    }

    // Preset "Chữ chạy" của mạch 4X (ma trận 16×16): chữ chạy + hiệu ứng 181 cộng màu lên trên (bm 2).
    private val chuChay = JSONObject("""{"on":true,"bri":160,"mainseg":1,"n":"Chữ chạy","seg":[
        {"id":0,"start":0,"stop":16,"startY":0,"stopY":16,"grp":1,"spc":0,"of":0,"on":true,"sel":true,
         "n":"Happy Smart Light","col":[[255,160,0],[0,0,0],[0,0,0]],"fx":122,"sx":255,"ix":128,"pal":18,"bm":0},
        {"id":1,"start":0,"stop":16,"startY":0,"stopY":16,"grp":1,"spc":0,"of":0,"on":true,"sel":true,
         "n":"","col":[[255,160,0],[0,0,0],[0,0,0]],"fx":181,"sx":243,"ix":153,"pal":0,"bm":2},
        {"stop":0}]}""")

    private fun chuClip(segment: String? = null) =
        ShowFile.Clip(segment, 0.0, 40.4, 0.1, "Chữ chạy", ShowFile.clipLook(chuChay), ShowFile.clipLooks(chuChay))

    @Test fun looksKeepEverySegmentWithItsArea() {
        val looks = ShowFile.clipLooks(chuChay)!!
        assertEquals(listOf(122, 181), looks.map { it.getInt("fx") })
        assertEquals(listOf(0, 2), looks.map { it.getInt("bm") })
        assertEquals("Happy Smart Light", looks[0].getString("n"))
        assertEquals("[0,16,0,16]", looks[1].getJSONArray("area").toString())
        assertTrue(ShowFile.LAYOUT_KEYS.none { looks[0].has(it) })
        assertNull(ShowFile.clipLooks(JSONObject().put("seg", JSONArray().put(chuChay.getJSONArray("seg").getJSONObject(0)))))
        val off = JSONObject(chuChay.getJSONArray("seg").getJSONObject(1).toString()).put("on", false)
        assertNull(ShowFile.clipLooks(JSONObject().put("seg", JSONArray().put(chuChay.getJSONArray("seg").getJSONObject(0)).put(off))))
        val strip = ShowFile.clipLooks(JSONObject("""{"seg":[{"id":0,"start":0,"stop":30,"fx":0},{"id":1,"start":30,"stop":60,"fx":9},{"id":2,"fx":28}]}"""))!!
        assertEquals(listOf("[0,30]", "[30,60]", null), strip.map { it.optJSONArray("area")?.toString() })
        val lo = ShowFile.clipLooks(JSONObject("""{"seg":[{"id":0,"start":0,"stop":14,"lo":true,"fx":1},{"id":1,"start":14,"stop":21,"lo":true,"fx":2}]}"""))!!
        assertEquals(listOf("[0,14]", "[14,21]"), lo.map { it.getJSONArray("area").toString() })
    }

    @Test fun boardRowClipCarriesEveryLayerOnV6() {
        val built = ShowFile.buildShow("T", emptyList(), listOf(chuClip()), 40.6, loop = false, layers = true)
        val clip = lines(built.text)[1]
        assertTrue(built.warnings.isEmpty())
        assertEquals(-1, clip.getInt("s"))
        val seg = clip.getJSONArray("seg")
        assertEquals(listOf(122, 181), (0 until seg.length()).map { seg.getJSONObject(it).getInt("fx") })
        assertEquals(2, seg.getJSONObject(1).getInt("bm"))
        assertEquals("[0,16,0,16]", seg.getJSONObject(0).getJSONArray("area").toString())
    }

    @Test fun oldPlayerAndSegmentRowPlayTheFirstLayerWithAWarning() {
        var built = ShowFile.buildShow("T", emptyList(), listOf(chuClip()), 40.6, loop = false)
        assertEquals(122, lines(built.text)[1].getJSONObject("seg").getInt("fx"))
        assertTrue(built.warnings[0].contains(ShowFile.LAYERS_FIRMWARE))
        built = ShowFile.buildShow("T", listOf(arm), listOf(chuClip("arm")), 40.6, loop = false, layers = true)
        assertEquals(122, lines(built.text)[1].getJSONObject("seg").getInt("fx"))
        assertTrue(built.warnings[0].contains("lớp đầu"))
    }

    @Test fun tooManyLayersAreCapped() {
        val many = JSONObject().put("seg", JSONArray().also { a ->
            for (i in 0 until ShowFile.LOOKS_MAX + 3) a.put(JSONObject().put("id", i).put("start", 0).put("stop", 10).put("fx", i))
        })
        val built = ShowFile.buildShow("T", emptyList(),
            listOf(ShowFile.Clip(null, 0.0, 1.0, 0.0, "x", ShowFile.clipLook(many), ShowFile.clipLooks(many))),
            1.0, loop = false, layers = true)
        assertEquals(ShowFile.LOOKS_MAX, lines(built.text)[1].getJSONArray("seg").length())
        assertTrue(built.warnings.isNotEmpty())
    }

    @Test fun readBackKeepsTheLayers() {
        val text = ShowFile.buildShow("T", emptyList(), listOf(chuClip()), 40.6, loop = false, layers = true).text
        val parsed = ShowFile.parseShow(text)!!.clips[0]
        assertEquals(122, parsed.look!!.getInt("fx"))
        assertFalse(parsed.look!!.has("area"))
        val segs = ShowFile.lookSegments(parsed.looks)
        assertEquals(2, segs.length())
        assertEquals(1, segs.getJSONObject(1).getInt("id"))
        assertEquals(16, segs.getJSONObject(1).getInt("stopY"))
        // công thức dựng lại → cùng các lớp
        val again = ShowFile.clipLooks(JSONObject().put("seg", segs))!!
        assertEquals(parsed.looks!!.map { PresetJson.canonical(it) }, again.map { PresetJson.canonical(it) })
        val lo = ShowFile.lookSegments(listOf(JSONObject("""{"fx":1,"area":[0,14]}"""), JSONObject("""{"fx":2}""")))
        assertEquals(true, lo.getJSONObject(0).getBoolean("lo"))
        assertFalse(lo.getJSONObject(1).has("start"))
    }

    @Test fun showFromTheDesktopToolParses() {
        // Dòng do desktop Tool v3.17.11 ghi lên mạch 4X (file thật, rút gọn).
        val text = "{\"v\":1,\"name\":\"Timecode 249\",\"dur\":40600,\"loop\":false,\"marks\":[1000,3000]}\n" +
            "{\"s\":-1,\"t\":0,\"d\":40400,\"tr\":100,\"name\":\"Chữ chạy\",\"seg\":[{\"fx\":122,\"n\":\"Happy Smart Light\",\"area\":[0,16,0,16]},{\"fx\":181,\"bm\":2,\"area\":[0,16,0,16]}]}\n"
        val p = ShowFile.parseShow(text)
        assertNotNull(p)
        assertEquals(40.6, p!!.duration, 1e-9)
        assertEquals(2, p.clips[0].looks!!.size)
        assertEquals("Chữ chạy", p.clips[0].name)
    }
}
