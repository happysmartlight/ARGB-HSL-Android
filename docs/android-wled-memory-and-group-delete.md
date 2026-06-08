# Android Guide: Hien Thi Bo Nho Va Xoa Logo/Preset Theo Nhom

Tai lieu nay tom tat logic dang dung trong app desktop de Android co the:

- Hien thi thong tin bo nho cua tung thiet bi WLED/ARGB HSL.
- Hien thi so luong preset theo nhom: logo/anh va timecode/preset.
- Xoa dung nhom logo/anh hoac nhom preset ma khong xoa nham file/preset cua nhom khac.

## 1. API Can Dung

Thay `{ip}` bang dia chi IP cua thiet bi.

| Muc dich | API | Ghi chu |
| --- | --- | --- |
| Doc state + info bo nho | `GET http://{ip}/json` | Lay `info.fs`, `info.wifi`, version, ten thiet bi. |
| Doc danh sach preset | `GET http://{ip}/presets.json` | Tra ve object key la preset ID dang string. |
| Xoa preset | `POST http://{ip}/json/state` body `{"pdel": 5}` | Xoa preset slot 5. |
| Dua thiet bi ve safe state | `POST /json/state` body `{"ps":0}`, sau do `{"on":false}` | Nen lam truoc khi xoa hang loat preset/file. |
| List file filesystem | `GET http://{ip}/edit?list` | Tra ve array file, thuong co `name`, `size`. Co the bi khoa PIN. |
| Xoa file | `GET http://{ip}/edit?func=delete&path=/file.gif` | Path nen bat dau bang `/`. Co the bi khoa PIN. |

Neu gap HTTP `401` o `/edit?list` hoac delete file, Android can hien flow nhap/mo khoa PIN cua WLED truoc khi retry.

## 2. Hien Thi Bo Nho Thiet Bi

Desktop dang lay bo nho tu `GET /json`:

```json
{
  "info": {
    "wifi": { "signal": 78 },
    "fs": { "u": 123456, "t": 983040 }
  }
}
```

Cong thuc:

```text
fsUsedBytes = info.fs.u
fsTotalBytes = info.fs.t
fsPercent = floor(fsUsedBytes * 100 / fsTotalBytes)
fsFreeBytes = fsTotalBytes - fsUsedBytes
```

Mau canh bao giong app desktop:

| Percent | Trang thai |
| --- | --- |
| `< 70%` | Xanh, con tot |
| `70% - 84%` | Vang, gan day |
| `>= 85%` | Do, can don bo nho |

Goi y UI:

```text
Bo nho: 63% (121 KB / 960 KB)
Wifi: 78%
Logo/anh: 12 preset
Preset timecode: 34/180 slot
```

## 3. Phan Nhom Preset Bang Slot ID

App desktop phan nhom bang ID preset, khong dua vao ten preset.

| Nhom | Slot ID | Muc dich |
| --- | --- | --- |
| Logo/anh | `1..59` | Logo, anh user, preset anh upload nhanh. |
| Timecode/preset app | `60..240`, bo qua `100` | Preset do app bake/quan ly cho timeline/timecode. Tong dung luong slot: `180`. |
| He thong | `100`, `248`, `249`, `250` | Slot dac biet, khong xoa khi xoa theo nhom. |
| Khac | ID khac | Nen hien thi rieng hoac de "Khac/Unknown". |

Slot he thong dang dung trong desktop:

| Slot | Y nghia |
| --- | --- |
| `100` | Autosave / backup WLED. |
| `248` | `TIMELINE_OFF`, preset tat hieu ung bang segment den, dung cho gap trong timeline. |
| `249` | Playlist/timecode fixed slot. |
| `250` | Nut bam vat ly / preset he thong. |

Android nen hien thi badge:

```text
Logo/anh: logoUsed
Timecode: timecodeUsed / 180
He thong: systemUsed / 4
```

Pseudocode:

```kotlin
val LOGO_RANGE = 1..59
val TIMECODE_RANGE = 60..240
val SYSTEM_SLOTS = setOf(100, 248, 249, 250)
val AUTOSAVE_SLOT = 100

fun classifyPreset(pid: Int): String {
    return when {
        pid in SYSTEM_SLOTS -> "system"
        pid in LOGO_RANGE -> "logo"
        pid in TIMECODE_RANGE && pid != AUTOSAVE_SLOT -> "timecode"
        else -> "other"
    }
}
```

## 4. Nhan Dien File Anh Ma Preset Dang Tham Chieu

Preset anh WLED thuong co field `seg[].n` tro toi file tren filesystem:

```json
{
  "n": "Logo HSL",
  "seg": [
    {
      "id": 0,
      "n": "/logo_hsl.gif",
      "fx": 53
    }
  ]
}
```

Chi coi `seg[].n` la file khi:

- La string khong rong.
- Co extension anh hop le: `.gif`, `.bmp`, `.png`, `.jpg`, `.jpeg`.
- Nen normalize thanh path co dau `/` o dau.

Pseudocode:

```kotlin
val IMAGE_EXTS = setOf(".gif", ".bmp", ".png", ".jpg", ".jpeg")

fun extractFileRefs(preset: JsonObject): Set<String> {
    val out = mutableSetOf<String>()
    val segs = preset["seg"] as? JsonArray ?: return out
    for (seg in segs) {
        val obj = seg as? JsonObject ?: continue
        val raw = obj["n"] as? String ?: continue
        if (raw.isBlank()) continue
        val path = if (raw.startsWith("/")) raw else "/$raw"
        val lower = path.lowercase()
        if (IMAGE_EXTS.any { lower.endsWith(it) }) {
            out += path
        }
    }
    return out
}
```

## 5. Nguyen Tac Xoa An Toan

Khong nen lam theo kieu:

```text
Delete all .gif/.bmp files
Delete all presets
```

Cach do de xoa nham anh logo hoac preset cua nhom khac.

Quy tac an toan:

1. Doc lai `/presets.json` moi nhat truoc khi xoa.
2. Chon target preset IDs theo nhom slot.
3. Lay danh sach file anh duoc target presets tham chieu.
4. Lay danh sach file anh duoc presets con lai tham chieu.
5. Chi xoa file anh neu file do thuoc target group va khong con preset nao ngoai target group tham chieu.
6. Khong bao gio xoa slot he thong `100`, `248`, `249`, `250` khi user chon xoa theo nhom.
7. Xoa tuan tu tren tung thiet bi, chen delay ngan sau moi lenh ghi flash.

## 6. Xoa Nhom Logo/Anh

Muc tieu:

- Xoa preset co ID `1..59`.
- Xoa file anh ma cac preset logo/anh do tham chieu, neu file khong con duoc preset khac dung.
- Khong xoa preset timecode `60..240`.
- Khong xoa slot he thong.

Flow:

```text
1. GET /presets.json
2. targetPids = preset IDs trong 1..59
3. targetFileRefs = union extractFileRefs(preset) cua targetPids
4. remainingFileRefs = union extractFileRefs(preset) cua cac preset khong bi xoa
5. filesToDelete = targetFileRefs - remainingFileRefs
6. POST /json/state {"ps":0}
7. sleep 100ms
8. POST /json/state {"on":false}
9. sleep 100ms
10. Voi moi pid trong targetPids sort tang dan:
    POST /json/state {"pdel": pid}
    sleep 150-200ms
11. Voi moi path trong filesToDelete:
    GET /edit?func=delete&path={urlEncode(path)}
    sleep 200ms
12. Refresh lai /json, /presets.json, /edit?list
```

Pseudocode:

```kotlin
suspend fun deleteLogoGroup(ip: String) {
    val presets = getPresets(ip)
    val targetPids = presets.keys
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 1..59 }
        .sorted()

    val targetRefs = mutableSetOf<String>()
    val remainingRefs = mutableSetOf<String>()

    for ((idText, preset) in presets) {
        val pid = idText.toIntOrNull() ?: continue
        if (preset !is JsonObject || preset.isEmpty()) continue
        val refs = extractFileRefs(preset)
        if (pid in targetPids) targetRefs += refs else remainingRefs += refs
    }

    val filesToDelete = targetRefs - remainingRefs

    safeState(ip)
    for (pid in targetPids) {
        postState(ip, mapOf("pdel" to pid))
        delay(200)
    }
    for (path in filesToDelete.sorted()) {
        deleteFile(ip, path)
        delay(200)
    }
}
```

## 7. Xoa Nhom Preset Timecode/App

Muc tieu:

- Xoa preset app/timecode trong `60..240`.
- Bo qua slot `100`.
- Khong xoa slot he thong `248`, `249`, `250`.
- Chi xoa file anh neu file do khong con duoc logo/anh hoac preset khac dung.

Flow giong xoa logo, chi khac bo loc target:

```kotlin
fun isTimecodeDeletable(pid: Int): Boolean {
    return pid in 60..240 && pid != 100 && pid !in setOf(248, 249, 250)
}
```

Luu y: slot `248`, `249`, `250` khong nam trong `60..240` theo range timecode hien tai, nhung van nen keep trong `SYSTEM_SLOTS` de tranh code sau nay doi range.

Pseudocode:

```kotlin
suspend fun deleteTimecodePresetGroup(ip: String) {
    val presets = getPresets(ip)
    val systemSlots = setOf(100, 248, 249, 250)
    val targetPids = presets.keys
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 60..240 && it !in systemSlots }
        .sorted()

    val targetRefs = mutableSetOf<String>()
    val remainingRefs = mutableSetOf<String>()

    for ((idText, preset) in presets) {
        val pid = idText.toIntOrNull() ?: continue
        if (preset !is JsonObject || preset.isEmpty()) continue
        val refs = extractFileRefs(preset)
        if (pid in targetPids) targetRefs += refs else remainingRefs += refs
    }

    val filesToDelete = targetRefs - remainingRefs

    safeState(ip)
    for (pid in targetPids) {
        postState(ip, mapOf("pdel" to pid))
        delay(200)
    }
    for (path in filesToDelete.sorted()) {
        deleteFile(ip, path)
        delay(200)
    }
}
```

## 8. Xoa File Anh Mo Coi (Optional)

Sau khi xoa preset theo nhom, co the con file anh mo coi tren filesystem.

Android co the them nut rieng:

```text
Don file anh khong duoc preset nao tham chieu
```

Flow:

```text
1. GET /presets.json
2. GET /edit?list
3. allImageFiles = file tren filesystem co ext .gif/.bmp/.png/.jpg/.jpeg
4. referencedFiles = union seg[].n cua tat ca preset
5. orphanFiles = allImageFiles - referencedFiles
6. Xac nhan voi user
7. GET /edit?func=delete&path=... tung file
```

Khong nen tu dong xoa orphan files trong action xoa preset neu chua hien preview/confirm, vi co the user upload file thu cong.

## 9. Ham HTTP Mau

```kotlin
suspend fun getInfo(ip: String): JsonObject =
    httpGetJson("http://$ip/json")

suspend fun getPresets(ip: String): Map<String, JsonObject> =
    httpGetJson("http://$ip/presets.json").asObjectMap()

suspend fun postState(ip: String, body: Map<String, Any>) {
    httpPostJson("http://$ip/json/state", body)
}

suspend fun safeState(ip: String) {
    postState(ip, mapOf("ps" to 0))
    delay(100)
    postState(ip, mapOf("on" to false))
    delay(100)
}

suspend fun deletePreset(ip: String, pid: Int) {
    postState(ip, mapOf("pdel" to pid))
}

suspend fun listFiles(ip: String): List<DeviceFile> =
    httpGetJson("http://$ip/edit?list").asFileList()

suspend fun deleteFile(ip: String, path: String) {
    val normalized = if (path.startsWith("/")) path else "/$path"
    val encoded = urlEncode(normalized)
    httpGet("http://$ip/edit?func=delete&path=$encoded")
}
```

## 10. UI/UX De Tranh Xoa Nham

Nen hien dialog confirm rieng cho tung action:

```text
Xoa Logo/Anh
- Se xoa 12 preset trong slot 1-59
- Se xoa 12 file anh khong con duoc preset khac dung
- Khong xoa preset timecode 60-240

Tiep tuc?
```

```text
Xoa Preset Timecode
- Se xoa 34 preset trong slot 60-240
- Bo qua slot he thong 100, 248, 249, 250
- Se xoa 0 file anh phu thuoc

Tiep tuc?
```

Sau khi xoa xong:

- Refresh `GET /json` de cap nhat bo nho `info.fs`.
- Refresh `GET /presets.json` de cap nhat badge preset.
- Refresh `GET /edit?list` neu UI co hien danh sach file.

## 11. Mapping Voi Desktop App

Logic desktop dang tham chieu:

- Bo nho filesystem: `main.py::_refresh_device_stats_async`, `GET /json -> info.fs.u/info.fs.t`.
- Preset mau: `ui/timeline_tab.py::_update_palette_count_badges`.
- Mau nhom preset: `ui/timeline_tab.py::PresetGroupDelegate`.
- Trich file anh tu preset: `ui/timeline_tab.py::extract_preset_file_refs`.
- Xoa preset: `clients/wled_client.py::delete_preset` gui `{"pdel": pid}`.
- List/xoa file: `clients/wled_client.py::list_files`, `delete_file_get`.

Quan trong: helper cu trong tab Matrix co nut xoa all preset/file theo extension. Android khong nen copy cach xoa all `.gif/.bmp` cho action theo nhom. Hay dung `seg[].n` reference tracking nhu tai lieu nay de khong xoa nham file cua nhom khac.
