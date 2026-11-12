# Task brief — Android: new timeline file fields (recipe, MAC, relative audio) + device-safety rules

> **Audience:** the AI developer working on the **Android app** (Kotlin).
> **Introduced by:** desktop Tool ARGB HSL **v3.17.0** and POI HSL Studio **v1.7.0** (released 2026-11-12).
> **Prerequisite:** the decrypt step from [`android-timecode-decrypt-task.md`](android-timecode-decrypt-task.md)
> is already in place. **Encryption did not change** (same container, same key, same test vector).
> **Format reference:** [`timecode-json-android-plugin.md`](timecode-json-android-plugin.md). Its §3.1 describes
> the same fields more briefly; this brief is the complete version, with algorithms and tests.

---

## 0. Why this changed

Until now a clip only stored a **slot number** (`preset_id`). Desktop users lost timelines or played the
wrong thing when a device was unplugged and reflashed, wiped, given a new IP by the router, or when the
timeline file was opened on another PC. If the preset at that slot was gone, or now held something
else, the playlist played the wrong effect or nothing at all.

The desktop app now writes, in the same `version: 1` file:

- **`clips[].recipe`**: the preset content the clip expects at `preset_id`, plus its images. The app can
  check the device and re-create the preset when it is missing.
- **`tracks[].mac`**: the device MAC, so a device that got a new IP can be found again.
- **`audio_rel`**: the music path relative to the timeline file.

The desktop app also changed how it talks to devices: it checks presets before playing after a
reconnect, reads `presets.json` back after every write, and sends Play to all devices at once. Section 5
describes these rules so the Android app can behave the same way.

---

## 1. What to do (checklist)

**Must.** Without these the Android app breaks on new files or plays the wrong presets:

- [ ] Keep accepting `version: 1`. Every new field is optional; ignore fields you do not know.
- [ ] A clip on a **real** track (`is_mock: false`) can now have `bake_snapshot`: the desktop saved the
      file before that clip finished writing to the device. Treat it exactly like a mock clip: bake it
      before compiling the playlist. Its `preset_id` is only a temporary slot and must never be played.
- [ ] Accept `preset_id` up to **1024**, and allocate free slots using the device's `pmax` (§4.6).
      Never use 251–255. Section 5 of the integration guide still says 60–240 only; it is outdated for
      firmware ≥ 2611090.
- [ ] If the app re-saves timeline files, keep every field you loaded, including unknown ones (§6).

**Should.** Needed to match the desktop app:

- [ ] Decode `clips[].recipe` and the root `recipe_files` (§4.1).
- [ ] Before Play or "save playlist" on a device, reconcile its clips with its `presets.json`
      (§4.3, §4.4), once per connection (§5.1).
- [ ] Cross-device mapping: a clip with `recipe` can now be baked on **any** device (§4.5). This replaces
      "warn or skip" in step 3d of the integration guide.
- [ ] Find a device whose IP changed using `tracks[].mac` (§4.2).
- [ ] Find the audio file using `audio_rel` (§4.7).
- [ ] After every `presets.json` upload, read it back and verify (§5.2).
- [ ] Send Play to all devices at the same moment (§5.4).

---

## 2. Compatibility

| Situation | Result |
|-----------|--------|
| Current Android app opens a new file | Parses fine (new fields are ignored), **but** a real-track clip with `bake_snapshot` plays a wrong or missing slot, and lost presets cannot be fixed. Hence the "Must" items. |
| Updated Android app opens an old file (no `recipe` / `mac` / `audio_rel`) | Must work as before. You can fill in recipes from the device (§4.4, case BACKFILL). |
| Desktop v3.17 opens a file saved by Android without the new fields | Works. The desktop reads the recipes back from the devices. |
| Desktop older than v3.17 opens a file with the new fields | Works. It ignores them. |

`version` stays **1**. Do not reject a file because a field is missing, and do not bump the version
when you write these fields.

---

## 3. New and changed fields

### 3.1 `tracks[i].clips[j].recipe` (object, optional)

```json
"recipe": {
  "kind": "preset",
  "payload": { "on": true, "bri": 128, "seg": [{ "id": 0, "fx": 122, "n": "/logo.bmp", "col": [[255, 160, 0]] }], "n": "Logo" },
  "files": [ { "path": "/logo.bmp", "size": 70, "sha256": "7e55b9b6…4625b56" } ]
}
```

| Field | Meaning |
|-------|---------|
| `kind` | `"effect"`, `"palette"` or `"preset"`. Informational. |
| `payload` | The WLED preset object that **should** be at `preset_id` on this track's device. Ready to write into `presets.json` as-is. |
| `files[].path` | Image path on the device (`/name.bmp`, `/name.gif`, …), referenced by `payload.seg[].n`. |
| `files[].size` | Byte length of the image. |
| `files[].sha256` | Lowercase hex SHA-256 of the image bytes. It is the key into the root `recipe_files` table. |

- The desktop writes it for clips whose content it knows: clips it baked, clips read from a device with
  "Load slot", clips dragged from a device preset, and clips it checked against a device.
- A clip can have **both** `recipe` and `bake_snapshot`. If `bake_snapshot` is present, the clip is not
  written to the device yet: bake from `bake_snapshot`.
- If the image bytes were not available when the file was saved, the desktop **omits that entry** from
  `files`. `payload` can therefore reference an image that `files` does not contain (§4.1).
- The desktop also accepts an inline `"content_b64"` inside a `files[]` entry when the sha is not in the
  table. It never writes one, but read it if present.

### 3.2 Root `recipe_files` (object, optional)

```json
"recipe_files": { "<sha256 hex>": "<standard base64 of the file bytes, with padding>" }
```

Each image is stored once, however many clips use it. It is only referenced from
`clips[].recipe.files[].sha256`. It may be `{}` or absent.

### 3.3 `tracks[i].mac` (string, optional, real devices only)

12 lowercase hex characters without separators, taken from `GET /json/info` → `info.mac`. Example:
`"3cdc75fef930"`. Mock tracks never have it. Normalize any MAC you compare: strip every non-hex
character and lowercase it. It must be exactly 12 characters and not `000000000000`; otherwise treat it
as "no MAC".

### 3.4 Root `audio_rel` (string or null, optional)

The music file path relative to the folder of the timeline file, with `/` separators, e.g.
`"music/song.mp3"`. It is `null` when the music is on another drive. `audio_path` is still the absolute
path on the PC that saved the file.

### 3.5 `bake_snapshot` (meaning widened)

Same shape as before. Images are still inline as `files[].content_b64`, and `pending_files` is never
written. It used to appear only on mock tracks; now it can also appear on a **real** track (see §1).

### 3.6 Unchanged

Encryption container, `version`, `mock_devices`, `timing_marks`, `defaults`, `audio_trim`,
`preset_palette`, `palette_library`, and the meaning of `start` / `duration` / `transition` and
`clip_type` / `fx_id` / `palette_id`.

---

## 4. Algorithms

### 4.1 Decode recipes

```kotlin
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private val IMAGE_EXTS = listOf(".gif", ".bmp", ".png", ".jpg", ".jpeg")

class RecipeFile(val path: String, val bytes: ByteArray)

class Recipe(
    val kind: String,
    val payload: JSONObject,          // WLED preset that belongs at preset_id
    val files: List<RecipeFile>,      // images to upload before the preset
    val missingFiles: List<String>,   // referenced by payload, but no bytes in the file
)

fun sha256Hex(b: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

/** Image paths a preset references (seg[].n = "/name.bmp"). Same rule as the desktop. */
fun presetFileRefs(payload: JSONObject): List<String> {
    val out = LinkedHashSet<String>()
    val segs = payload.optJSONArray("seg") ?: return emptyList()
    for (i in 0 until segs.length()) {
        val n = segs.optJSONObject(i)?.optString("n").orEmpty()
        if (n.isEmpty()) continue
        val path = if (n.startsWith("/")) n else "/$n"
        if (IMAGE_EXTS.any { path.lowercase().endsWith(it) }) out += path
    }
    return out.toList()
}

/** cache: sha256 -> bytes, so an image shared by many clips is decoded once. */
fun parseRecipe(entry: JSONObject?, table: JSONObject?, cache: MutableMap<String, ByteArray>): Recipe? {
    if (entry == null) return null
    val payload = entry.optJSONObject("payload") ?: return null
    val files = mutableListOf<RecipeFile>()
    val missing = mutableListOf<String>()
    val arr = entry.optJSONArray("files") ?: JSONArray()
    for (i in 0 until arr.length()) {
        val f = arr.optJSONObject(i) ?: continue
        val path = f.optString("path")
        if (path.isEmpty()) continue
        val sha = f.optString("sha256")
        var bytes: ByteArray? = if (sha.isNotEmpty()) cache[sha] else null
        if (bytes == null) {
            var b64 = if (sha.isNotEmpty() && table != null && table.has(sha)) table.optString(sha) else ""
            if (b64.isEmpty()) b64 = f.optString("content_b64")          // tolerated fallback
            bytes = if (b64.isEmpty()) null
                    else runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            if (bytes != null && sha.isNotEmpty()) {
                if (sha256Hex(bytes) != sha) bytes = null else cache[sha] = bytes
            }
        }
        if (bytes == null) { missing += path; continue }
        files += RecipeFile(path, bytes)
    }
    for (ref in presetFileRefs(payload)) {
        if (files.none { it.path == ref } && ref !in missing) missing += ref
    }
    return Recipe(entry.optString("kind", "preset"), payload, files, missing)
}
```

`missingFiles` is not an error by itself: the target device may already have the image. Check it with
`GET /edit?list` before baking (§4.5).

### 4.2 Find the device of a real track (MAC)

```text
for each real track (is_mock == false):
  1. GET http://{track.ip}/json/info  (timeout ~2 s)
     - answers, and track.mac is absent or normMac(info.mac) == track.mac → use track.ip
     - answers with a DIFFERENT mac → that IP now belongs to another device; do not use it for this track
  2. otherwise, if track.mac is present: look among discovered devices (mDNS _hsl._tcp + known IPs)
     for one whose /json/info mac == track.mac → move the track to that IP,
     but only if no other track already uses that device (never merge two tracks)
  3. not found → keep the track and its clips, shown as offline. Never drop clips.
```

Hint: the mDNS hostname is `hsl-` + the **last 6** hex digits of the MAC (`hsl-fef930` for
`3cdc75fef930`). It is enough to choose which device to probe first, but always confirm with the full
`info.mac`.

The desktop reads the MAC once per connection, only for devices that have clips. It reads every online
device only when some track's device is missing.

### 4.3 Compare preset content

Two presets count as "the same content" when they are equal **ignoring only the top-level `n` / `name`**.
`seg[].n` is still compared, because it is the image path. A preset whose only content is its name never
matches anything. Desktop reference: `TimelineTab._canonical_preset_content`.

```kotlin
fun samePresetContent(a: JSONObject?, b: JSONObject?): Boolean {
    if (a == null || b == null) return false
    val ka = a.keys().asSequence().filterNot { it == "n" || it == "name" }.toSet()
    val kb = b.keys().asSequence().filterNot { it == "n" || it == "name" }.toSet()
    if (ka.isEmpty() || ka != kb) return false
    return ka.all { jsonEquals(a.opt(it), b.opt(it)) }
}

fun jsonEquals(x: Any?, y: Any?): Boolean = when {
    x is JSONObject && y is JSONObject -> {
        val kx = x.keys().asSequence().toSet()
        kx == y.keys().asSequence().toSet() && kx.all { jsonEquals(x.opt(it), y.opt(it)) }
    }
    x is JSONArray && y is JSONArray ->
        x.length() == y.length() && (0 until x.length()).all { jsonEquals(x.opt(it), y.opt(it)) }
    x is Boolean || y is Boolean -> x == y
    x is Number && y is Number -> x.toDouble() == y.toDouble()
    else -> x == y
}
```

Always compare **parsed JSON**, never file text. The firmware leaves spaces in `presets.json` after a
`pdel`, and the key order is not guaranteed.

### 4.4 Reconcile a track with its device

Read `GET /presets.json` from the device, then:

```text
index = map content -> first slot, built from every numeric key holding a non-empty object
        (keys in ascending numeric order; skip 100 = autosave, it changes constantly)

for each clip of the track:
  if clip.bake_snapshot != null: continue            // will be baked anyway (§1)
  cur = presets[clip.preset_id] if it is a non-empty object, else null
  if clip.recipe != null:
      if samePresetContent(cur, recipe.payload):          OK
      else if (slot = index.find(recipe.payload)) != null: REPOINTED -> clip.preset_id = slot
      else:                                               LOST -> re-create from the recipe (§4.5)
  else:                                              // old file / old clip
      if cur == null:  UNKNOWN    -> warn "preset N missing on <device>, no recipe to restore it";
                                     keep the clip, mark it (desktop shows ⚠)
      else:            BACKFILLED -> clip.recipe = Recipe("preset", cur, images downloaded from the device)
```

Cases worth noticing:

- The slot now holds **different** content: the result is LOST, not OK. The playlist must not play
  whatever is at that slot now.
- A REPOINTED clip changes its `preset_id`. Compile the playlist **after** reconciling.
- The desktop marks LOST clips ⚠ and re-creates them when the user presses Play / Save, or on demand
  from the track menu ("♻ Kiểm tra & ghi lại clip"). Android can re-create them right away before
  playing.

### 4.5 Re-create a preset from a recipe (= bake)

This is the same flow as baking a `bake_snapshot`; only the source of payload and files differs.

```text
1. info = GET /json/info;  pmax = info.fs.pmax ?: 250
2. images: for each recipe.files entry → POST /upload (once per path per device);
   for each recipe.missingFiles entry → GET /edit?list; if the device lacks it, this clip cannot be
   baked → report it and skip the clip (do not write a preset that references a missing image)
3. presets = GET /presets.json
4. for every clip of this device that needs baking:
       slot = findFreeSlot(presets, pmax)  (§4.6);  presets[slot] = payload
5. upload presets.json ONCE (compact form, §5.2), then read back and verify (§5.2)
6. per clip: preset_id = slot;  bake_snapshot = null;  recipe = {kind, payload, files}
```

Upload images **before** `presets.json`. Batch all clips of a device into one `presets.json` upload.

### 4.6 Free slot allocation (firmware ≥ 2611090 supports up to 1024)

```text
pmax  = GET /json/info → fs.pmax   (missing on older firmware → 250; do NOT guess from vid)
order = 60..240, then 256..pmax    (the second range only when pmax ≥ 256)
skip  = 100, 248, 249, 250, every slot 251..255, every slot holding a non-empty object
```

On a `pmax = 250` device, never propose an ID above 250. The firmware silently ignores
`psave` / `pdel` on invalid IDs, so a write to 251–255 or above `pmax` just disappears.

### 4.7 Find the audio file

`audio_path` is a PC path. On Android, try in this order:

1. `audio_rel`, resolved against the folder the timeline file was opened from (if your SAF/document
   access covers that folder);
2. a file with the same name as the last segment of `audio_path` (split on both `/` and `\`) in that
   folder;
3. otherwise ask the user to pick the file. Keep the clips; only the music is missing.

`audio_trim` is unchanged.

---

## 5. Device-side rules (match the desktop)

### 5.1 Check presets once per connection

- Keep a set `verified` of device IPs whose `presets.json` was reconciled (§4.4) since they last
  connected.
- Remove an IP when the device disconnects or the device list is rebuilt (rescan). After a reconnect
  it may have been reflashed or wiped, or the IP may belong to another device.
- Before **Play** or **Save playlist**, for every online device that has clips and is not in `verified`:
  read `presets.json`, reconcile, re-create LOST clips. If everything matched, continue the action
  automatically. If something was re-created, finish baking first. The desktop asks the user to press
  Play again; continuing automatically after baking is also fine.
- A failed read still counts as "tried" (add the IP to `verified`), so Play is never blocked forever.
  Play then reports its own error for that device.

### 5.2 Upload `presets.json` safely

- **Compact form**: `"0":{}` first, numeric keys in ascending order, no spaces, real UTF-8. Example:
  `{"0":{},"60":{"n":"TMP Rainbow","seg":[{"id":0,"fx":9,"pal":5,"col":[[255,0,0]]}]},"248":{…}}`.
  The firmware edits the file in place. A file with indentation or spaces after `:` becomes invalid
  JSON after a later `pdel` (measured on real devices). Newer firmware re-compacts the file itself, but
  many devices still run older firmware.
- **Read back**: after the upload, `GET /presets.json` and check that every slot you wrote is a
  non-empty object. Retry the read up to 3 times, 0.4 s apart. If a slot is still missing, the write
  failed: keep the clip unbaked and report it. The firmware can drop a write without any error.
- Recommended: keep a local copy of the `presets.json` you read before overwriting it. The desktop
  keeps the last 10 per device.
- Never send `{"pdel": 250}`: slot 250 is the physical button preset. Never write or delete 251–255.

### 5.3 Saving the playlist (slot 249)

Before uploading, check that **every** ID in `playlist.ps` exists (as a non-empty object) in the
`presets.json` you are about to upload. If one is missing, refuse and tell the user; a playlist that
calls a missing preset plays nothing for that part. Payloads:

```json
"249": { "playlist": { "ps": [60, 248, 61], "dur": [30, 10, 20], "transition": [5, 0, 0], "repeat": 0, "end": 0, "r": 0 }, "on": true, "n": "Timecode 249" }
"248": { "n": "TIMELINE_OFF", "seg": [{ "id": 0, "fx": 0, "col": [[0, 0, 0]] }] }
```

### 5.4 Play on all devices at the same moment

Sending to devices one after another made later devices start 50–300 ms late. The desktop now sends
in two phases:

1. **Prepare**, in parallel and not synchronized: for each device whose playlist uses 248 and whose 248
   you have not confirmed during this connection, read `presets.json`, write 248, then upload and
   verify (§5.2).
2. **Send**, synchronized: one coroutine per device. All of them wait at a gate (maximum 2 s), then
   `POST /json/state` at the same instant:
   - inline playlist: `{"on":true,"seg":[{"id":0,"frz":false}],"playlist":{…}}`
   - saved slot: `{"on":true,"seg":[{"id":0,"frz":false}],"ps":249}`

On two real devices the desktop measured 0.1–0.2 ms between the two sends.

```kotlin
import kotlinx.coroutines.*

suspend fun sendTogether(ips: List<String>, bodyFor: (String) -> String,
                         post: (String, String) -> Unit): Map<String, Result<Unit>> =
    coroutineScope {
        val ready = java.util.concurrent.CountDownLatch(ips.size)
        val gate = CompletableDeferred<Unit>()
        val jobs = ips.associateWith { ip ->
            async(Dispatchers.IO) {
                val body = bodyFor(ip)          // build everything before the gate
                ready.countDown()
                gate.await()
                runCatching { post(ip, body) }
            }
        }
        withContext(Dispatchers.IO) { ready.await(2, java.util.concurrent.TimeUnit.SECONDS) }
        gate.complete(Unit)
        jobs.mapValues { it.value.await() }
    }
```

### 5.5 Send real UTF-8

Every JSON body (state, `presets.json`, names) must be sent as UTF-8 bytes **without** `\uXXXX`
escapes. The firmware stores escapes literally: a preset named "thử" would become `th\u1eed`. Use
`Content-Type: application/json; charset=utf-8`.

---

## 6. If the Android app writes timeline files

- Keep `version: 1` and the same encryption.
- Edit the `JSONObject` you loaded and write it back. Do not rebuild the file only from your model, or
  fields you do not know are lost. Note that the desktop rebuilds the file on save, so keys it does not
  know (for example your own `android_*` keys) do not survive a desktop re-save.
- After baking a clip: set `preset_id` to the real slot, remove `bake_snapshot`, and set
  `recipe = {kind, payload, files:[{path, size, sha256}]}`. Put each image in
  `recipe_files[sha256] = base64`:
  - sha256 as lowercase hex of the exact bytes;
  - standard base64 with padding and **no line breaks**: `Base64.encodeToString(bytes, Base64.NO_WRAP)`.
    `Base64.DEFAULT` inserts `\n` every 76 characters.
- If you do not have an image's bytes, leave that entry out of `files` rather than writing a wrong
  sha. Never write `bytes`, `pending_files`, or other runtime-only fields.
- `tracks[].mac`: 12 lowercase hex characters from `/json/info`. If you moved a track to a new IP
  (§4.2), write the new `ip`.
- `audio_rel`: relative to the folder you save into, with `/` separators. Write `null` if you cannot
  compute it.

---

## 7. Sample file (exact desktop output, decrypted)

Generated by the desktop `export_config`, not typed by hand. It shows a real track with a baked effect
clip, a preset clip with an image, a clip **not yet baked** on a real track, a second real track
sharing the same image, and a mock track.

```json
{
  "version": 1,
  "preset_palette": {
    "source_ip": null,
    "presets": []
  },
  "palette_library": {
    "source_ip": null,
    "preview_signature": [],
    "palettes": [],
    "preview_data": {}
  },
  "audio_path": "D:\\Show\\2026\\music\\song.mp3",
  "audio_rel": "music/song.mp3",
  "audio_trim": null,
  "recipe_files": {
    "7e55b9b6b0d4f0fa1115e70c888d6dc0b80d9358938214180c67c8fef4625b56": "Qk1GAAAAAAAAADYAAAAoAAAAAgAAAAIAAAABABgAAAAAABAAAADEDgAAxA4AAAAAAAAAAAAAAAD/AAD/AAAAAP8AAP8AAA=="
  },
  "mock_devices": [
    {"id": "mock:1", "name": "Stage Left"}
  ],
  "tracks": [
    {
      "ip": "192.168.1.10",
      "name": "Gậy 1",
      "is_mock": false,
      "clips": [
        {
          "preset_id": 60,
          "name": "Rainbow",
          "start": 0.0,
          "duration": 5.0,
          "transition": 0.5,
          "clip_type": "effect",
          "fx_id": 9,
          "palette_id": 5,
          "recipe": {
            "kind": "effect",
            "payload": {
              "n": "TMP Rainbow",
              "seg": [
                {
                  "id": 0,
                  "fx": 9,
                  "pal": 5,
                  "col": [[255, 0, 0]]
                }
              ]
            },
            "files": []
          }
        },
        {
          "preset_id": 1,
          "name": "Logo",
          "start": 6.0,
          "duration": 4.0,
          "transition": 0.0,
          "recipe": {
            "kind": "preset",
            "payload": {
              "on": true,
              "bri": 128,
              "seg": [
                {
                  "id": 0,
                  "fx": 122,
                  "n": "/logo.bmp",
                  "col": [[255, 160, 0]]
                }
              ],
              "n": "Logo"
            },
            "files": [
              {
                "path": "/logo.bmp",
                "size": 70,
                "sha256": "7e55b9b6b0d4f0fa1115e70c888d6dc0b80d9358938214180c67c8fef4625b56"
              }
            ]
          }
        },
        {
          "preset_id": 61,
          "name": "Blink",
          "start": 10.0,
          "duration": 2.0,
          "transition": 0.0,
          "clip_type": "effect",
          "fx_id": 1,
          "bake_snapshot": {
            "kind": "effect",
            "payload": {
              "n": "TMP Blink",
              "seg": [
                {
                  "id": 0,
                  "fx": 1,
                  "col": [[0, 0, 255]]
                }
              ]
            },
            "files": []
          }
        }
      ],
      "mac": "aabbccddee01"
    },
    {
      "ip": "192.168.1.11",
      "name": "Gậy 2",
      "is_mock": false,
      "clips": [
        {
          "preset_id": 1,
          "name": "Logo",
          "start": 0.0,
          "duration": 4.0,
          "transition": 0.0,
          "recipe": {
            "kind": "preset",
            "payload": {
              "on": true,
              "bri": 128,
              "seg": [
                {
                  "id": 0,
                  "fx": 122,
                  "n": "/logo.bmp",
                  "col": [[255, 160, 0]]
                }
              ],
              "n": "Logo"
            },
            "files": [
              {
                "path": "/logo.bmp",
                "size": 70,
                "sha256": "7e55b9b6b0d4f0fa1115e70c888d6dc0b80d9358938214180c67c8fef4625b56"
              }
            ]
          }
        }
      ],
      "mac": "aabbccddee02"
    },
    {
      "ip": "mock:1",
      "name": "Stage Left",
      "is_mock": true,
      "clips": [
        {
          "preset_id": 62,
          "name": "Fire",
          "start": 0.0,
          "duration": 3.0,
          "transition": 0.3,
          "clip_type": "effect",
          "fx_id": 66,
          "bake_snapshot": {
            "kind": "effect",
            "payload": {
              "n": "TMP Fire",
              "seg": [
                {
                  "id": 0,
                  "fx": 66,
                  "col": [[255, 80, 0]]
                }
              ]
            },
            "files": []
          }
        }
      ]
    }
  ],
  "timing_marks": [],
  "defaults": {"duration": 3.0, "transition": 0.7, "slot": 249, "loop": false}
}
```

Facts to assert in a unit test:

- `recipe_files` has exactly 1 entry. Its value decodes to 70 bytes starting with `BM`, and
  `sha256Hex(bytes)` equals the key `7e55b9b6…4625b56`.
- Both "Logo" clips (on two different tracks) reference that same sha. With the cache, the image is
  decoded once.
- On track `192.168.1.10`, clip "Blink" has `bake_snapshot` on a **real** track. It must be baked
  before playing, and slot 61 must never appear in a compiled playlist unless the bake assigned it.
- Clip "Rainbow" has name `Rainbow`, but its recipe payload's top-level `n` is `"TMP Rainbow"`. The
  top-level name is ignored when comparing content (§4.3).
- `audio_rel` = `music/song.mp3`, relative to `D:\Show\2026` (the folder of the timeline file).
- The mock track has no `mac`.

---

## 8. Acceptance tests

1. An old file without the new fields loads and plays exactly as before.
2. The sample above: recipes decode, the sha matches, and the shared image is decoded only once.
3. Reconcile against a device:
   - (a) the slot holds the same content → OK, no write;
   - (b) the content was moved to another slot → `preset_id` changes, the playlist uses the new slot;
   - (c) the preset was deleted → re-created, and present after read-back;
   - (d) the slot holds **different** content → re-created; the wrong preset is never played;
   - (e) no recipe and an empty slot → warning shown, clip kept.
4. A real-track clip with `bake_snapshot` is baked before compiling; the temporary slot is never sent.
5. MAC:
   - (a) the track IP is offline and the device answers at another IP with the same MAC → the track
     moves there;
   - (b) the target already has a track with clips → no merge;
   - (c) the old IP answers with another MAC → that IP is not used for this track.
6. Slots: on a `pmax = 1024` device with 60–240 full, a bake uses 256+. On a `pmax = 250` device,
   nothing above 250 is ever used.
7. Write verification: a device that "accepts" the upload but does not store the slot → error, clip
   stays unbaked.
8. Save playlist refuses when a preset referenced in `ps` is missing on the device.
9. Play on 2+ devices: the sends start within a few milliseconds of each other (log the timestamps).
10. Vietnamese preset or device names reach the device without `\u` escapes.
11. Save on Android, then open on desktop v3.17: tracks, clips, recipes and `mac` are intact, and the
    fields of the original file are preserved.

---

## 9. Pitfalls

- `Base64.DEFAULT` adds line breaks: decoding works, but write with `NO_WRAP`.
- Compare preset content structurally (§4.3), not as text. Top-level `n` is ignored; `seg[].n` is not.
- `presets.json` keys are strings, in no particular order, and go up to `"1024"` on new firmware.
- An IP is not an identity: after a reboot the same IP can belong to another device. Check the MAC.
- Firmware silently ignores invalid IDs (251–255, above `pmax`) and can silently drop a write. Always
  read back.
- Never `pdel` 250. Never upload `presets.json` with indentation.
- Keep clips of devices you cannot reach. Show them offline; never delete them.

---

## 10. Desktop source of truth

| Topic | Where |
|-------|-------|
| Recipe pack / unpack, `norm_mac`, audio path resolution, atomic file write | `services/timeline_store.py` |
| Export / import of the file | `ui/timeline_tab.py`: `TimelineTab.export_config`, `_apply_config` |
| Reconcile with a device | `TimelineTab._reconcile_ip_with_presets`, `_verify_tracks`, `_canonical_preset_content` |
| Check once per connection, check before Play | `_verified_ips`, `_unverified_clip_ips`, `_wait_for_verify` |
| MAC re-mapping | `_fetch_device_macs`, `_reconcile_tracks_by_mac` |
| Safe upload + read-back | `_upload_presets_verified`, `services/preset_ops.presets_json_text` |
| Free slots / `pmax` | `services/preset_groups.py` (`max_preset_id`, `group_slots`), `TimelineView.find_free_temp_slot` |
| Simultaneous Play | `_parallel_map(start_together=True)` |
| Tests | `tests/test_timeline_store.py`, `tests/test_timeline_recovery.py`, `tests/test_timeline_data_safety.py` |
