package com.example.data

import com.example.data.PlaylistTiming.Entry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Đối chiếu desktop `_slice_entries_at_offset` / `_apply_loaded_slot` + đo thật trên 2X Pro FW 2611103. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistTimingTest {

    private fun parse(json: String) = PlaylistTiming.parse(JSONObject(json))!!

    @Test
    fun stepLengthIsDurOnlyTransitionIsInside() {
        // Đo trên mạch thật: dur 20 + transition 10 → đổi preset mỗi 2.0 s (không phải 3.0 s).
        val p = parse("""{"ps":[1,2],"dur":[20,20],"transition":[10,10],"repeat":1}""")
        assertEquals(4.0, p.cycleSeconds, 1e-9)
        assertEquals(listOf(0.0 to 2.0, 2.0 to 4.0), p.spans())
        assertFalse(p.loop)
    }

    @Test
    fun scalarDurAndTransitionApplyToEveryEntry() {
        val p = parse("""{"ps":[3,4,5],"dur":50,"transition":7}""")
        assertEquals(listOf(Entry(3, 50, 7), Entry(4, 50, 7), Entry(5, 50, 7)), p.entries)
        assertTrue("thiếu repeat = lặp mãi", p.loop)
    }

    @Test
    fun missingDurDefaultsToTenSecondsAndZeroIsClampedToOne() {
        val p = parse("""{"ps":[1,2],"dur":[0]}""")
        assertEquals(listOf(1, 100), p.entries.map { it.durDs })
    }

    @Test
    fun emptyOrMissingPlaylistIsNull() {
        assertNull(PlaylistTiming.parse(null))
        assertNull(PlaylistTiming.parse(JSONObject("""{"ps":[]}""")))
    }

    @Test
    fun sliceInsideEntryShortensItAndDropsTransition() {
        val p = parse("""{"ps":[1,248,2],"dur":[30,20,50],"transition":[5,0,7],"repeat":1}""")
        // 0–3 s: 1 · 3–5 s: OFF · 5–10 s: 2. Tua tới 6.5 s → còn 3.5 s của preset 2.
        assertEquals(listOf(Entry(2, 35, 0)), PlaylistTiming.slice(p, 6.5, loop = false))
        // Đúng mốc bắt đầu 1 mục → giữ nguyên mục (kể cả transition).
        assertEquals(listOf(Entry(248, 20, 0), Entry(2, 50, 7)), PlaylistTiming.slice(p, 3.0, loop = false))
    }

    @Test
    fun sliceWithLoopWrapsTheHeadToTheEnd() {
        val p = parse("""{"ps":[1,2,3],"dur":[40,40,20],"transition":[4,4,4],"repeat":0}""")
        val s = PlaylistTiming.slice(p, 5.0, loop = true)
        // 5 → 8: phần còn lại của 2 (3 s) · 3 (2 s) · rồi quay về 1 (4 s) · 2 phần đầu (1 s).
        assertEquals(listOf(Entry(2, 30, 0), Entry(3, 20, 4), Entry(1, 40, 4), Entry(2, 10, 4)), s)
        assertEquals(p.cycleSeconds, s.sumOf { it.durDs } / 10.0, 1e-9)
    }

    @Test
    fun sliceAtZeroOrEndPlaysFromTheStart() {
        val p = parse("""{"ps":[1,2],"dur":[10,10],"repeat":0}""")
        assertEquals(p.entries, PlaylistTiming.slice(p, 0.0, loop = true))
        assertEquals(p.entries, PlaylistTiming.slice(p, 2.0, loop = true))
        assertEquals(p.entries, PlaylistTiming.slice(p, 1.98, loop = false))
    }

    @Test
    fun toJsonWritesArraysAndRepeat() {
        val j = PlaylistTiming.toJson(listOf(Entry(7, 12, 3)), loop = false)
        assertEquals("""{"ps":[7],"dur":[12],"transition":[3],"repeat":1,"end":0,"r":0}""", j.toString())
        assertEquals(0, PlaylistTiming.toJson(listOf(Entry(7, 12, 3)), loop = true).getInt("repeat"))
    }

    @Test
    fun clipsFromPlaylistTreatOffSlotAsGap() {
        val p = parse("""{"ps":[248,61,62,248,63],"dur":[15,20,30,5,40],"transition":[0,7,0,0,3],"repeat":0}""")
        val clips = PlaylistTiming.clipsFromPlaylist(p)
        assertEquals(listOf(61, 62, 63), clips.map { it.presetId })
        assertEquals(listOf(1.5, 3.5, 7.0), clips.map { it.startSec })
        assertEquals(listOf(2.0, 3.0, 4.0), clips.map { it.durationSec })
        assertEquals(listOf(0.7, 0.0, 0.3), clips.map { it.transitionSec })
    }

    @Test
    fun resumeEntriesWrapsLoopAndStopsAfterOneShotEnd() {
        val loop = parse("""{"ps":[1,2],"dur":[20,20],"repeat":0}""")
        // Vòng 2, giây 5 → tương đương giây 1 của vòng → còn 1 s preset 1.
        assertEquals(Entry(1, 10, 0), PlaylistTiming.resumeEntries(loop, 5.0)!!.first())
        val once = parse("""{"ps":[1,2],"dur":[20,20],"repeat":1}""")
        assertEquals(listOf(Entry(2, 10, 0)), PlaylistTiming.resumeEntries(once, 3.0))
        assertNull("đã qua cuối playlist chạy 1 lần", PlaylistTiming.resumeEntries(once, 4.5))
    }

    @Test
    fun resumeJsonNeverSendsOneShotPlaylists() {
        // FW 2611103 (playlist lồng): playlist chạy 1 lần nạp đè lên 249 → hết là nạp lại 249 ở giữa chừng.
        val once = PlaylistTiming.resumeJson(listOf(Entry(2, 30, 0), Entry(3, 50, 7)), loop = false)
        assertEquals(0, once.getInt("repeat"))
        assertEquals("[30,36000]", once.getJSONArray("dur").toString())
        val loop = PlaylistTiming.resumeJson(listOf(Entry(2, 30, 0), Entry(3, 50, 7)), loop = true)
        assertEquals("[30,50]", loop.getJSONArray("dur").toString())
        assertEquals(0, loop.getInt("repeat"))
        val hold = PlaylistTiming.holdPlaylistJson()
        assertEquals("""{"ps":[0],"dur":[36000],"transition":[0],"repeat":0,"end":0,"r":0}""", hold.toString())
    }

    @Test
    fun repeatNIsExpandedToOneShot() {
        val p = PlaylistTiming.expandRepeats(parse("""{"ps":[1,2],"dur":[10,20],"repeat":3}"""))
        assertEquals(6, p.entries.size)
        assertEquals(9.0, p.cycleSeconds, 1e-9)
        assertFalse(p.loop)
        val loop = parse("""{"ps":[1],"dur":[10],"repeat":0}""")
        assertTrue(PlaylistTiming.expandRepeats(loop) === loop)
    }

    @Test
    fun editablePresetsSkipSystemReservedAndPlaylistsAndClassifyKinds() {
        val presets = JSONObject(
            """{"0":{},
               "1":{"n":"Logo POI","seg":[{"id":0,"fx":142,"n":"logo.bmp"}]},
               "2":{"n":"Cờ","seg":[{"id":0,"fx":53,"n":"/flag.gif"}]},
               "3":{"n":"Cầu vồng","seg":[{"id":0,"fx":9}]},
               "4":{"n":"Ds khác","playlist":{"ps":[1,2],"dur":[10,10]}},
               "100":{"n":"autosave"},"248":{"n":"TIMELINE_OFF"},"249":{"n":"TL","playlist":{"ps":[1]}},
               "250":{"n":"btn"},"252":{"n":"reserved"},
               "300":{"n":"Timecode xa","seg":{"id":0,"fx":142}}}"""
        )
        val list = TimelineEditing.editablePresets(presets)
        assertEquals(listOf(1, 2, 3, 300), list.map { it.first })
        assertEquals(
            listOf(PresetKind.POI, PresetKind.FLAG, PresetKind.EFFECT, PresetKind.POI),
            list.map { it.third }
        )
        assertEquals(listOf(4), TimelineEditing.nestedPlaylistRefs(presets, listOf(1, 248, 4, 4, 3)))
    }

    @Test
    fun searchIgnoresDiacriticsAndMatchesId() {
        assertTrue(TimelineEditing.matches(12, "Rồng Lửa Đỏ", "rong lua"))
        assertTrue(TimelineEditing.matches(12, "Rồng Lửa Đỏ", "do"))
        assertTrue(TimelineEditing.matches(12, "x", "12"))
        assertFalse(TimelineEditing.matches(12, "x", "1"))
        assertTrue(TimelineEditing.matches(12, "x", "  "))
    }

    @Test
    fun compileThenLoadRoundTrips() {
        val entries = listOf(
            TimecodePlanner.PlaylistEntry(slot = 61, start = 1.0, duration = 2.5, transition = 0.7),
            TimecodePlanner.PlaylistEntry(slot = 62, start = 3.5, duration = 4.0, transition = 0.0)
        )
        val compiled = TimecodePlanner.compilePlaylist(entries, timelineSeconds = 9.0, loop = true)
        val back = PlaylistTiming.clipsFromPlaylist(PlaylistTiming.parse(compiled.playlist)!!)
        assertEquals(listOf(61 to 1.0, 62 to 3.5), back.map { it.presetId to it.startSec })
        assertEquals(9.0, PlaylistTiming.parse(compiled.playlist)!!.cycleSeconds, 1e-9)
    }
}
