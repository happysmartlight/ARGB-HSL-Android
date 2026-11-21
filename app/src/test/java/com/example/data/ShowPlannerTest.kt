package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nạp track file timeline desktop lên mạch có trình phát show (ShowPlanner) + dựng lại clip tab Biên Tập. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowPlannerTest {
    private val chuChay = JSONObject("""{"n":"Chữ chạy","seg":[
        {"id":0,"start":0,"stop":16,"startY":0,"stopY":16,"n":"Happy Smart Light","fx":122,"col":[[255,160,0]],"bm":0},
        {"id":1,"start":0,"stop":16,"startY":0,"stopY":16,"fx":181,"sx":243,"bm":2}]}""")
    private val solid = JSONObject("""{"n":"Đỏ","seg":[{"id":0,"start":0,"stop":16,"fx":0,"col":[[255,0,0]]}]}""")
    private val gif = JSONObject("""{"n":"Logo","seg":[{"id":0,"fx":53,"n":"/logo.gif"}]}""")

    private fun recipe(payload: JSONObject) =
        JSONObject().put("kind", "preset").put("payload", payload).put("files", JSONArray())

    private fun clip(name: String, start: Double, dur: Double, pid: Int = -1, segment: String? = null, payload: JSONObject? = null) =
        JSONObject().apply {
            put("name", name); put("preset_id", pid); put("start", start); put("duration", dur); put("transition", 0.2)
            segment?.let { put("segment", it) }
            payload?.let { put("recipe", recipe(it)) }
        }

    private fun track(clips: List<JSONObject>, segments: List<ShowFile.Segment> = emptyList()) =
        TimelineTrack(0, "192.168.0.130", "4X", false, "3cdc75fef930", clips, segments)

    private val head = ShowFile.Segment("Đầu", 0, 14, key = "head", ledOrder = true)
    private val arm = ShowFile.Segment("Tay", 14, 21, key = "arm", ledOrder = true)

    @Test fun showTrackKeepsSegmentsLayersAndBoardPresets() {
        val presets = JSONObject().put("61", solid)
        val t = track(listOf(
            clip("Chữ chạy", 0.0, 10.0, payload = chuChay),
            clip("Đỏ trên tay", 2.0, 3.0, segment = "arm", payload = solid),
            clip("Preset cũ", 10.0, 2.0, pid = 61),                       // file cũ: không kèm nội dung
            clip("Đoạn đã xoá", 12.0, 1.0, segment = "gone", payload = solid),
            clip("Khoảng tối", 13.0, 1.0)                                  // preset_id -1, không nội dung
        ), listOf(head, arm))
        val plan = ShowPlanner.plan(t, null, HashMap(), presets, null, 7)
        assertTrue(plan.problems.isEmpty())
        assertEquals(listOf("Chữ chạy", "Đỏ trên tay", "Preset cũ", "Đoạn đã xoá"), plan.clips.map { it.name })
        assertEquals(listOf(null, "arm", null, null), plan.clips.map { it.segment })  // đoạn không còn → toàn mạch
        assertEquals(2, plan.clips[0].looks!!.size)
        assertEquals(1, plan.segmentClipCount)
        assertEquals(1, plan.layeredClipCount)
        assertEquals(0, plan.clips[2].look!!.getInt("fx"))
        val text = ShowFile.buildShow("Timecode 249", t.segments, plan.clips, 14.0, false, layers = true).text
        val p = ShowFile.parseShow(text)!!
        assertEquals(listOf("head", "arm"), p.segments.map { it.key })
        assertTrue(p.segments.all { it.ledOrder })
        assertEquals(2, p.clips.first { it.name == "Chữ chạy" }.looks!!.size)
    }

    @Test fun missingPresetAndOldPlayerAreProblems() {
        var plan = ShowPlanner.plan(track(listOf(clip("Mất", 0.0, 1.0, pid = 77))), null, HashMap(), JSONObject(), null, 7)
        assertEquals(1, plan.problems.size)
        // phân đoạn Thứ tự LED trên trình phát chưa hiểu "lo" (trước v5)
        plan = ShowPlanner.plan(track(listOf(clip("x", 0.0, 1.0, payload = solid)), listOf(head)), null, HashMap(), JSONObject(), null, 4)
        assertTrue(plan.problems.single().contains(ShowFile.LED_ORDER_FIRMWARE))
    }

    @Test fun imageCheckedOnTheBoardWhenTheFileHasNoBytes() {
        val t = track(listOf(clip("Logo", 0.0, 2.0, payload = gif)))
        var plan = ShowPlanner.plan(t, null, HashMap(), JSONObject(), null, 7)
        assertTrue(plan.needsDeviceFiles)
        plan = ShowPlanner.plan(t, null, HashMap(), JSONObject(), setOf("/logo.gif"), 7)
        assertTrue(plan.problems.isEmpty())
        assertFalse(plan.needsDeviceFiles)
        plan = ShowPlanner.plan(t, null, HashMap(), JSONObject(), setOf("/khac.gif"), 7)
        assertTrue(plan.problems.single().contains("/logo.gif"))
    }

    @Test fun oldFirmwareGetsOnlyTheBoardRow() {
        val t = track(listOf(clip("a", 0.0, 1.0, payload = solid), clip("b", 0.0, 1.0, segment = "arm", payload = solid),
            clip("c", 1.0, 1.0, segment = "gone", payload = solid)), listOf(arm))
        val (board, dropped) = ShowPlanner.boardRowClips(t)
        assertEquals(listOf("a", "c"), board.map { it.getString("name") })
        assertEquals(1, dropped)
        assertEquals(1, t.segmentClipCount)
    }

    @Test fun showClipFindsThePresetWithTheSameLook() {
        val presets = JSONObject()
            .put("100", chuChay)                                   // autosave: không dùng
            .put("60", solid)
            .put("62", chuChay)
            .put("249", ShowFile.playPreset("Timecode 249"))
        val layered = ShowFile.Clip(null, 0.0, 1.0, 0.0, "x", ShowFile.clipLook(chuChay), ShowFile.clipLooks(chuChay))
        assertEquals(62, ShowPlanner.presetForClip(presets, layered))
        val text = ShowFile.buildShow("T", emptyList(), listOf(layered), 1.0, false, layers = true).text
        assertEquals(62, ShowPlanner.presetForClip(presets, ShowFile.parseShow(text)!!.clips[0]))
        // show ghi cho trình phát cũ: chỉ lớp đầu → vẫn nhận ra preset nhiều lớp
        val firstOnly = ShowFile.parseShow(ShowFile.buildShow("T", emptyList(), listOf(layered), 1.0, false).text)!!.clips[0]
        assertEquals(62, ShowPlanner.presetForClip(presets, firstOnly))
        assertEquals(60, ShowPlanner.presetForClip(presets, ShowFile.Clip(null, 0.0, 1.0, 0.0, "y", ShowFile.clipLook(solid))))
        assertNull(ShowPlanner.presetForClip(presets, ShowFile.Clip(null, 0.0, 1.0, 0.0, "z", JSONObject("""{"fx":99}"""))))
    }

    @Test fun offPresetMatchesTheDesktop() {
        val off = TimecodePlanner.timelineOffPreset()
        assertEquals("TIMELINE_OFF", off.getString("n"))
        assertEquals(64, off.getJSONArray("seg").length())
        assertTrue(off.getJSONObject("show").getBoolean("stop"))
    }
}
