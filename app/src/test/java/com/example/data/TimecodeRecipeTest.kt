package com.example.data

import com.example.data.TimecodePlanner.Action
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kiểm thử chấp nhận của docs/android-timecode-recipe-task.md §7–§8 (phần không cần mạng).
 * `sample_v317.json` = file mẫu §7 (đầu ra thật của desktop `export_config`, đã giải mã);
 * `presets_2xpro.json` = presets.json đọc từ mạch 2X Pro thật (firmware 2611103, pmax 1024).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimecodeRecipeTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("timecode/$name")!!.bufferedReader(Charsets.UTF_8).readText()

    private val sample by lazy { TimecodeFile.parse(JSONObject(resource("sample_v317.json"))) }

    private fun clip(name: String, pid: Int, start: Double, dur: Double, extra: JSONObject.() -> Unit = {}) =
        JSONObject().apply {
            put("preset_id", pid); put("name", name); put("start", start); put("duration", dur); put("transition", 0.0)
            extra()
        }

    private fun fxPreset(fx: Int, name: String = "P$fx") =
        JSONObject("""{"n":"$name","seg":[{"id":0,"fx":$fx,"col":[[255,0,0]]}]}""")

    private fun recipeOf(payload: JSONObject) = JSONObject().put("kind", "effect").put("payload", payload).put("files", JSONArray())

    // ---- §7: file mẫu -------------------------------------------------------------------------

    @Test
    fun sampleRecipeImageDecodesOnceAndMatchesSha() {
        val table = sample.recipeTable!!
        assertEquals(1, table.length())
        val sha = table.keys().next()
        val cache = HashMap<String, ByteArray>()
        val logos = sample.tracks.filter { !it.isMock }.flatMap { t -> t.clips.filter { it.optString("name") == "Logo" } }
        assertEquals(2, logos.size)
        val recipes = logos.map { RecipeCodec.parseRecipe(it.optJSONObject("recipe"), table, cache)!! }
        assertEquals(1, cache.size)
        val bytes = recipes[0].files.single().bytes
        assertEquals(70, bytes.size)
        assertEquals('B'.code.toByte(), bytes[0]); assertEquals('M'.code.toByte(), bytes[1])
        assertEquals(sha, sha256Hex(bytes))
        assertTrue(recipes.all { it.missingFiles.isEmpty() && it.files.single().path == "/logo.bmp" })
        // Cùng một mảng byte từ cache (giải mã đúng 1 lần).
        assertTrue(recipes[0].files.single().bytes === recipes[1].files.single().bytes)
    }

    @Test
    fun sampleTracksMacAndAudio() {
        val real = sample.tracks.filter { !it.isMock }
        assertEquals(listOf("aabbccddee01", "aabbccddee02"), real.map { it.mac })
        val mock = sample.tracks.single { it.isMock }
        assertEquals("", mock.mac)
        assertEquals("Stage Left", mock.name)
        assertEquals("music/song.mp3", sample.audioRel)
        assertEquals("song.mp3", sample.audioFileName)
        assertEquals(1, real[0].pendingBakeCount)
    }

    @Test
    fun rainbowRecipeNameIgnoredWhenComparing() {
        val rainbow = sample.tracks[0].clips.first { it.optString("name") == "Rainbow" }
        val payload = rainbow.getJSONObject("recipe").getJSONObject("payload")
        assertEquals("TMP Rainbow", payload.getString("n"))
        val onDevice = JSONObject(payload.toString()).put("n", "Rainbow")
        assertTrue(PresetJson.samePresetContent(payload, onDevice))
    }

    // ---- §4.3 so sánh nội dung ----------------------------------------------------------------

    @Test
    fun presetComparisonRules() {
        val a = JSONObject("""{"n":"A","on":true,"seg":[{"id":0,"fx":122,"n":"/a.bmp","sx":128}]}""")
        val sameOtherOrder = JSONObject("""{"seg":[{"sx":128.0,"n":"/a.bmp","fx":122,"id":0}],"on":true,"n":"B"}""")
        val otherImage = JSONObject("""{"n":"A","on":true,"seg":[{"id":0,"fx":122,"n":"/b.bmp","sx":128}]}""")
        assertTrue(PresetJson.samePresetContent(a, sameOtherOrder))
        assertFalse(PresetJson.samePresetContent(a, otherImage))                // seg[].n vẫn so
        assertFalse(PresetJson.samePresetContent(JSONObject("""{"n":"X"}"""), JSONObject("""{"n":"X"}""")))
        assertFalse(PresetJson.samePresetContent(JSONObject("""{"on":true}"""), JSONObject("""{"on":1}""")))
    }

    // ---- §5.2 ghi gọn --------------------------------------------------------------------------

    @Test
    fun presetsJsonTextIsCompactOrderedUtf8() {
        val p = JSONObject()
        p.put("249", JSONObject().put("n", "thử").put("on", true))
        p.put("60", JSONObject().put("seg", JSONArray().put(JSONObject().put("n", "/logo.bmp").put("fx", 1))))
        p.put("1024", JSONObject().put("n", "x"))
        p.put("0", JSONObject())
        val text = PresetJson.presetsJsonText(p)
        assertEquals("""{"0":{},"60":{"seg":[{"n":"/logo.bmp","fx":1}]},"249":{"n":"thử","on":true},"1024":{"n":"x"}}""", text)
        assertFalse(text.contains("\\u"))
        assertFalse(text.contains("\\/"))
        assertFalse(text.contains(": "))
        // Đọc lại được, cùng nội dung.
        assertEquals("thử", JSONObject(text).getJSONObject("249").getString("n"))
    }

    @Test
    fun realDevicePresetsRoundTripCompact() {
        val raw = resource("presets_2xpro.json")
        val presets = JSONObject(raw)
        val text = PresetJson.presetsJsonText(presets)
        assertTrue(text.startsWith("{\"0\":{},\"1\":"))
        val back = JSONObject(text)
        for (k in presets.keys()) assertEquals(PresetJson.canonical(presets.opt(k)), PresetJson.canonical(back.opt(k)))
    }

    // ---- §4.6 slot / pmax ---------------------------------------------------------------------

    @Test
    fun freeSlotUsesExtendedRangeOnlyWhenPmaxAllows() {
        val full = JSONObject()
        for (s in 60..240) full.put(s.toString(), fxPreset(1))
        assertEquals(256, PresetSlots.findFreeSlot(full, 1024))
        assertNull(PresetSlots.findFreeSlot(full, 250))
        val slots = PresetSlots.timecodeSlots(1024)
        assertTrue(slots.none { it in PresetSlots.RESERVED || it in PresetSlots.SYSTEM })
        assertEquals(1024, slots.last())
        assertTrue(PresetSlots.timecodeSlots(250).all { it <= 240 })
        // Slot "{}" coi là trống; 100 luôn bị bỏ qua.
        val p = JSONObject().put("60", JSONObject())
        assertEquals(60, PresetSlots.findFreeSlot(p, 250))
        for (s in 60..99) p.put(s.toString(), fxPreset(2))
        assertEquals(101, PresetSlots.findFreeSlot(p, 250))
    }

    @Test
    fun pmaxHintAndLabels() {
        assertEquals(250, PresetSlots.pmaxOrLegacy(null))
        assertEquals(250, PresetSlots.pmaxOrLegacy(0))
        assertEquals(1024, PresetSlots.pmaxOrLegacy(1024))
        assertEquals(300, PresetSlots.pmaxHint(null, JSONObject().put("300", fxPreset(1)).put("253", fxPreset(1))))
        assertEquals("1–250, 256–1024", PresetSlots.validRangeLabel(1024))
        assertEquals("1–250", PresetSlots.validRangeLabel(250))
        assertTrue(PresetSlots.isTimecodeSlot(256)); assertFalse(PresetSlots.isTimecodeSlot(253)); assertFalse(PresetSlots.isTimecodeSlot(249))
    }

    @Test
    fun normalizesMac() {
        assertEquals("aabbccddee01", normMac("AA:BB:CC:DD:EE:01"))
        assertEquals("3cdc75fef930", normMac("3c-dc-75-fe-f9-30"))
        assertEquals("", normMac("000000000000"))
        assertEquals("", normMac("aabbcc"))
        assertEquals("", normMac(null))
    }

    // ---- §4.4 / §8.3 đối chiếu ----------------------------------------------------------------

    private fun planOne(presets: JSONObject, clip: JSONObject, pmax: Int = 250, files: Set<String>? = null) =
        TimecodePlanner.plan(listOf(clip), null, HashMap(), presets, pmax, sameDevice = true, deviceFiles = files)

    @Test
    fun reconcileSameContentIsOk() {
        val presets = JSONObject().put("70", fxPreset(9, "Rainbow"))
        val plan = planOne(presets, clip("Rainbow", 70, 0.0, 5.0) { put("recipe", recipeOf(fxPreset(9, "TMP Rainbow"))) })
        assertEquals(Action.OK, plan.clips.single().action)
        assertEquals(70, plan.clips.single().slot)
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun reconcileMovedContentRepoints() {
        val presets = JSONObject().put("80", fxPreset(9))
        val plan = planOne(presets, clip("Rainbow", 70, 0.0, 5.0) { put("recipe", recipeOf(fxPreset(9))) })
        assertEquals(Action.REPOINTED, plan.clips.single().action)
        val applied = TimecodePlanner.apply(plan, presets, 250, 5.0, false)
        assertEquals(listOf(80), applied.ps)
    }

    @Test
    fun reconcileDeletedOrDifferentContentIsRecreated() {
        // (c) đã bị xoá
        val empty = JSONObject()
        val c = clip("Rainbow", 70, 0.0, 5.0) { put("recipe", recipeOf(fxPreset(9))) }
        val p1 = planOne(empty, c)
        assertEquals(Action.BAKE, p1.clips.single().action)
        val a1 = TimecodePlanner.apply(p1, empty, 250, 5.0, false)
        assertEquals(60, p1.clips.single().slot)
        assertTrue(a1.writtenSlots.containsAll(listOf(60, 249)))
        assertTrue(PresetJson.samePresetContent(empty.getJSONObject("60"), fxPreset(9)))
        // (d) slot giờ chứa nội dung KHÁC → không bao giờ phát preset sai đó
        val other = JSONObject().put("70", fxPreset(66, "Fire"))
        val p2 = planOne(other, c)
        assertEquals(Action.BAKE, p2.clips.single().action)
        val a2 = TimecodePlanner.apply(p2, other, 250, 5.0, false)
        assertFalse(a2.ps.contains(70))
    }

    @Test
    fun reconcileNoRecipeEmptySlotIsReported() {
        val plan = planOne(JSONObject(), clip("Old", 75, 0.0, 5.0))
        assertEquals(Action.UNRESOLVED, plan.clips.single().action)
        assertEquals(1, plan.problems.size)
        // Có preset ở slot → dùng như cũ (BACKFILL)
        val ok = planOne(JSONObject().put("75", fxPreset(3)), clip("Old", 75, 0.0, 5.0))
        assertEquals(Action.EXISTING, ok.clips.single().action)
        assertTrue(ok.problems.isEmpty())
    }

    // ---- §8.4: bake_snapshot trên track thật ---------------------------------------------------

    @Test
    fun realTrackSnapshotIsBakedAndTempSlotNeverPlayed() {
        val track = sample.tracks[0]
        // Slot tạm 61 của "Blink" đang giữ preset khác trên mạch.
        val presets = JSONObject().put("61", fxPreset(5, "Khác"))
        val plan = TimecodePlanner.plan(track.clips, sample.recipeTable, sample.recipeCache, presets, 250, true, null)
        val blink = plan.clips.single { it.name == "Blink" }
        assertEquals(Action.BAKE, blink.action)
        val applied = TimecodePlanner.apply(plan, presets, 250, track.endSeconds, false)
        assertNotEquals(61, blink.slot)
        assertFalse(applied.ps.contains(61))
        assertTrue(plan.uploads.containsKey("/logo.bmp"))
        assertTrue(TimecodePlanner.missingPlaylistRefs(presets, applied.ps, 250).isEmpty())
    }

    @Test
    fun reimportSameSnapshotReusesExistingSlot() {
        val mock = sample.tracks.single { it.isMock }
        val presets = JSONObject()
        val first = TimecodePlanner.plan(mock.clips, null, HashMap(), presets, 250, false, null)
        TimecodePlanner.apply(first, presets, 250, 3.0, false)
        val slot = first.clips.single().slot
        val second = TimecodePlanner.plan(mock.clips, null, HashMap(), presets, 250, false, null)
        assertEquals(Action.REPOINTED, second.clips.single().action)
        assertEquals(slot, second.clips.single().slot)
    }

    @Test
    fun identicalBakesInOneRunShareSlot() {
        val r = recipeOf(fxPreset(12))
        val clips = listOf(clip("A", 70, 0.0, 2.0) { put("recipe", r) }, clip("B", 71, 2.0, 2.0) { put("recipe", r) })
        val presets = JSONObject()
        val plan = TimecodePlanner.plan(clips, null, HashMap(), presets, 250, false, null)
        val applied = TimecodePlanner.apply(plan, presets, 250, 4.0, false)
        assertEquals(listOf(60, 60), applied.ps)
    }

    // ---- §4.5 ảnh thiếu -------------------------------------------------------------------------

    @Test
    fun missingImageNeedsDeviceCheck() {
        val payload = JSONObject("""{"n":"Logo","seg":[{"id":0,"fx":122,"n":"/x.bmp"}]}""")
        val c = clip("Logo", 5, 0.0, 3.0) { put("recipe", JSONObject().put("kind", "preset").put("payload", payload)) }
        val first = planOne(JSONObject(), c)
        assertTrue(first.needsDeviceFiles)
        assertTrue(first.problems.isEmpty())
        val lacking = planOne(JSONObject(), c, files = setOf("/other.bmp"))
        assertEquals(Action.UNRESOLVED, lacking.clips.single().action)
        val present = planOne(JSONObject(), c, files = setOf("/x.bmp"))
        assertEquals(Action.BAKE, present.clips.single().action)
    }

    // ---- §8.6 slot trên mạch thật (2X Pro, pmax 1024) -------------------------------------------

    @Test
    fun realDevicePresetsMatchAndExtendedRange() {
        val presets = JSONObject(resource("presets_2xpro.json"))
        val poi = presets.getJSONObject("1")
        // Clip có recipe đúng nội dung preset 1 (tên khác) → OK, không ghi gì.
        val okPlan = planOne(presets, clip("POI", 1, 0.0, 4.0) { put("recipe", recipeOf(JSONObject(poi.toString()).put("n", "khác"))) }, pmax = 1024)
        assertEquals(Action.OK, okPlan.clips.single().action)
        // Slot 100 (autosave) không bao giờ là đích trỏ lại.
        assertFalse(PresetJson.contentIndex(presets).containsValue(100))
        // 60–240 đầy → bake vào 256.
        for (s in 60..240) if (s !in PresetSlots.SYSTEM) presets.put(s.toString(), fxPreset(1000 + s))
        val bake = planOne(presets, clip("New", 70, 0.0, 4.0) { put("recipe", recipeOf(fxPreset(77))) }, pmax = 1024)
        TimecodePlanner.apply(bake, presets, 1024, 4.0, false)
        assertEquals(256, bake.clips.single().slot)
    }

    @Test(expected = TimecodePlanner.NoFreeSlotException::class)
    fun legacyDeviceNeverGoesAbove250() {
        val presets = JSONObject()
        for (s in 60..240) presets.put(s.toString(), fxPreset(1000 + s))
        val bake = planOne(presets, clip("New", 70, 0.0, 4.0) { put("recipe", recipeOf(fxPreset(77))) }, pmax = 250)
        TimecodePlanner.apply(bake, presets, 250, 4.0, false)
    }

    // ---- playlist ------------------------------------------------------------------------------

    @Test
    fun compilePlaylistGapsTrailingOffAndNoDrift() {
        val e = listOf(
            TimecodePlanner.PlaylistEntry(60, 1.0, 2.0, 0.5),
            TimecodePlanner.PlaylistEntry(61, 3.0, 1.0, 0.0),
            TimecodePlanner.PlaylistEntry(62, 5.0, 1.0, 0.0)
        )
        val c = TimecodePlanner.compilePlaylist(e, 8.0, loop = true)
        assertEquals(listOf(248, 60, 61, 248, 62, 248), c.ps)
        assertEquals("[10,20,10,10,10,20]", c.playlist.getJSONArray("dur").toString())
        assertEquals("[0,5,0,0,0,0]", c.playlist.getJSONArray("transition").toString())
        assertEquals(0, c.playlist.getInt("repeat"))
        // 30 clip 0.33 s: tổng vẫn đúng 9.9 s (không trôi do làm tròn từng clip).
        val many = (0 until 30).map { TimecodePlanner.PlaylistEntry(60, it * 0.33, 0.33, 0.0) }
        val m = TimecodePlanner.compilePlaylist(many, 9.9, loop = false)
        val dur = m.playlist.getJSONArray("dur")
        assertEquals(99, (0 until dur.length()).sumOf { dur.getInt(it) })
        assertFalse("không chèn OFF giả giữa clip liền nhau", m.ps.contains(248))
        assertEquals(30, m.ps.size)
        assertEquals(1, m.playlist.getInt("repeat"))
    }

    @Test
    fun offPlaceholderClipKeepsTiming() {
        val clips = listOf(clip("Gap", 0, 0.0, 2.0), clip("A", 70, 2.0, 1.0) { put("recipe", recipeOf(fxPreset(4))) })
        val presets = JSONObject()
        val plan = TimecodePlanner.plan(clips, null, HashMap(), presets, 250, false, null)
        val applied = TimecodePlanner.apply(plan, presets, 250, 3.0, false)
        assertEquals(listOf(248, 60), applied.ps)
        assertEquals("[20,10]", applied.playlist.getJSONArray("dur").toString())
        assertNotNull(presets.optJSONObject("248"))
        assertEquals(1, applied.clipCount)
    }

    @Test
    fun missingPlaylistRefsDetectsInvalidIds() {
        val presets = JSONObject().put("60", fxPreset(1)).put("300", fxPreset(2)).put("252", fxPreset(3))
        assertEquals(listOf(61, 252, 300), TimecodePlanner.missingPlaylistRefs(presets, listOf(60, 61, 252, 300), 250))
        assertEquals(listOf(61, 252), TimecodePlanner.missingPlaylistRefs(presets, listOf(60, 61, 252, 300), 1024))
    }

    // ---- §4.2 / §8.5 MAC ---------------------------------------------------------------------

    private fun realTrack(index: Int, ip: String, mac: String) =
        TimelineTrack(index, ip, "T$index", isMock = false, mac = mac, clips = emptyList())

    @Test
    fun macMatchFollowsDeviceToNewIp() {
        val m = TrackMatcher.autoMatch(
            listOf(realTrack(0, "192.168.1.10", "aabbccddee01")),
            listOf(TrackMatcher.Candidate("192.168.1.10", null), TrackMatcher.Candidate("192.168.1.23", "AA:BB:CC:DD:EE:01"))
        )
        assertEquals(TrackMatcher.Match("192.168.1.23", TrackMatcher.Kind.MAC_NEW_IP), m[0])
    }

    @Test
    fun macMatchNeverMergesTwoTracks() {
        // Track 1 khớp MAC mạch .23; track 0 cũng ở IP .23 trong file → không được gộp.
        val m = TrackMatcher.autoMatch(
            listOf(realTrack(0, "192.168.1.23", ""), realTrack(1, "192.168.1.50", "aabbccddee02")),
            listOf(TrackMatcher.Candidate("192.168.1.23", "aabbccddee02"))
        )
        assertEquals(TrackMatcher.Kind.MAC_NEW_IP, m[1]!!.kind)
        assertNull(m[0]!!.ip)
    }

    @Test
    fun oldIpWithOtherMacIsNotUsed() {
        val m = TrackMatcher.autoMatch(
            listOf(realTrack(0, "192.168.1.10", "aabbccddee01")),
            listOf(TrackMatcher.Candidate("192.168.1.10", "112233445566"))
        )
        assertEquals(TrackMatcher.Match(null, TrackMatcher.Kind.IP_OTHER_DEVICE), m[0])
    }

    @Test
    fun productListIncludes2xPro() {
        assertTrue(WledDevice.isValidProduct("ARGB HSL Controller 2X Pro"))
        assertTrue(WledDevice.isValidProduct("  argb hsl controller 2x pro "))
        assertTrue(WledDevice.isValidProduct("ARGB HSL Controller 4X"))
        assertFalse(WledDevice.isValidProduct("WLED"))
    }
}
