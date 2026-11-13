package com.example.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nút "Xóa preset + ảnh": ảnh đi cùng preset, ảnh dùng chung được giữ (desktop preset_ops). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetCleanupTest {

    private fun img(name: String, path: String) = JSONObject("""{"n":"$name","seg":[{"id":0,"fx":122,"n":"$path"}]}""")
    private fun fx(name: String) = JSONObject("""{"n":"$name","seg":[{"id":0,"fx":9}]}""")

    @Test
    fun deletableSkipsSystemReservedAndEmpty() {
        val p = JSONObject()
        for (id in listOf(1, 60, 100, 248, 249, 250, 252, 300)) p.put(id.toString(), fx("P$id"))
        p.put("0", JSONObject()).put("61", JSONObject())
        assertEquals(listOf(1, 60, 249, 300), PresetCleanup.deletablePids(p))
        assertEquals(PresetCleanup.Group.LOGO, PresetCleanup.groupOf(1))
        assertEquals(PresetCleanup.Group.TIMECODE, PresetCleanup.groupOf(300))
        assertEquals(PresetCleanup.Group.PLAYLIST, PresetCleanup.groupOf(249))
        assertEquals(PresetCleanup.Group.OTHER, PresetCleanup.groupOf(245))
    }

    @Test
    fun imageGoesWithPresetButSharedImageIsKept() {
        val p = JSONObject()
            .put("1", img("A", "/a.bmp"))
            .put("2", img("B", "/shared.gif"))
            .put("3", img("C", "shared.gif"))          // cùng ảnh, không có "/" đầu
            .put("100", img("autosave", "/auto.bmp"))  // slot hệ thống vẫn tính là "còn dùng"
            .put("4", img("D", "/auto.bmp"))
        val files = listOf("a.bmp", "/SHARED.GIF", "/auto.bmp", "cfg.json")
        val plan = PresetCleanup.plan(p, files, listOf(1, 2, 4))
        assertEquals(listOf(1, 2, 4), plan.pids)
        assertEquals(listOf("/a.bmp"), plan.files)   // shared.gif còn preset 3 dùng; auto.bmp còn slot 100 dùng
        // Xoá cả 2 và 3 → shared.gif đi theo (tên đúng như trên mạch).
        val both = PresetCleanup.plan(p, files, listOf(2, 3))
        assertEquals(listOf("/SHARED.GIF"), both.files)
        assertEquals(listOf(2, 3), both.filePids["/SHARED.GIF"])
    }

    @Test
    fun imageKeptWhenItsPresetWasNotReallyDeleted() {
        val p = JSONObject().put("2", img("B", "/s.gif")).put("3", img("C", "/s.gif")).put("5", img("E", "/e.bmp"))
        val plan = PresetCleanup.plan(p, listOf("/s.gif", "/e.bmp"), listOf(2, 3, 5))
        assertEquals(listOf("/e.bmp", "/s.gif"), plan.files)
        // pdel 3 không thành công → giữ s.gif.
        assertEquals(listOf("/e.bmp"), plan.filesAfter(setOf(2, 5)))
        assertEquals(listOf("/e.bmp", "/s.gif"), plan.filesAfter(setOf(2, 3, 5)))
    }

    @Test
    fun missingOnDeviceOrNoFileListMeansNoImageDeleted() {
        val p = JSONObject().put("1", img("A", "/a.bmp"))
        assertTrue(PresetCleanup.plan(p, listOf("/other.bmp"), listOf(1)).files.isEmpty())
        val noList = PresetCleanup.plan(p, emptyList(), listOf(1))
        assertEquals(listOf(1), noList.pids)
        assertTrue(noList.files.isEmpty())
    }

    @Test
    fun protectedSlotsNeverPlannedEvenIfRequested() {
        val p = JSONObject().put("100", fx("auto")).put("248", fx("off")).put("250", fx("btn")).put("253", fx("x"))
        assertTrue(PresetCleanup.plan(p, emptyList(), listOf(100, 248, 250, 253)).isEmpty)
    }

    @Test
    fun orphanImagesAndExtraFiles() {
        val p = JSONObject().put("1", img("A", "/a.bmp")).put("2", JSONObject("""{"n":"seg object","seg":{"id":0,"n":"/b.gif"}}"""))
        val files = listOf("/a.bmp", "/b.gif", "/old.bmp", "/x.png", "/presets.json", "/cfg.json")
        assertEquals(listOf("/old.bmp", "/x.png"), PresetCleanup.orphanImages(p, files))
        // Ảnh thừa xoá được kể cả khi không xoá preset nào; ảnh preset còn dùng thì bỏ qua.
        val plan = PresetCleanup.plan(p, files, emptyList(), extraFiles = listOf("/old.bmp", "/a.bmp"))
        assertEquals(listOf("/old.bmp"), plan.files)
        assertTrue(plan.filesAfter(emptySet()).contains("/old.bmp"))
    }

    @Test
    fun realTwoXProDevice() {
        val presets = JSONObject(javaClass.classLoader!!.getResourceAsStream("timecode/presets_2xpro.json")!!.bufferedReader().readText())
        // Danh sách file thật của mạch (GET /edit?list=/ trả tên không có "/").
        val files = listOf(
            "PULSE08-200px_3.bmp", "bkp.cfg.json", "cfg.json", "hsl-soi-toc-rub_disc.bmp",
            "hsl-song-nhat-hoang-.bmp", "presets.json", "version-info.json"
        )
        assertEquals(listOf(1, 2), PresetCleanup.deletablePids(presets))   // 100, 248 luôn chừa
        val plan = PresetCleanup.plan(presets, files, listOf(1))
        assertEquals(listOf("/hsl-song-nhat-hoang-.bmp"), plan.files)
        assertEquals(listOf("/PULSE08-200px_3.bmp"), PresetCleanup.orphanImages(presets, files))
        assertEquals("hsl-song-nhat-hoang-", PresetCleanup.presetName(presets, 1))
    }
}
