# Timecode JSON — Android Plugin Integration Guide

> **Context:** This document describes the `timecode_config.json` file format produced by the
> **HSL Tool** (PySide6 desktop app for ARGB HSL/WLED-style LED controllers), and the
> algorithm an Android plugin must implement to:
> 1. Parse the file
> 2. Collect mock (virtual) and real device info
> 3. Let the user map mock → real devices
> 4. Bake presets onto real WLED devices
> 5. Upload the compiled playlist

---

## 1. Glossary

| Term | Meaning |
|------|---------|
| **Mock device** | A virtual/offline device used for layout. IP prefix `mock:N` (e.g. `mock:1`). Has no real network address. |
| **Real device** | A physical ARGB HSL Controller running WLED firmware, reachable via HTTP at its local IP. |
| **Preset** | A named LED state stored in `presets.json` on the device, identified by an integer slot ID. |
| **Bake** | The act of writing a preset payload to the device's `presets.json` at a specific slot. |
| **bake_snapshot** | JSON blob stored in the timecode file that contains the full WLED preset payload for a clip that was created on a mock device (not yet uploaded to a real device). |
| **Playlist** | A WLED playlist object stored as a special preset that sequences other presets with timing. |
| **TEMP_PRESET_RANGE** | Slot range `[60, 240]` used for app-managed (temporary/baked) presets. |

---

## 2. Full JSON Schema

```json
{
  "version": 1,

  "preset_palette": {
    "source_ip": "192.168.1.10",
    "presets": [
      { "id": 1, "name": "Rainbow" },
      { "id": 2, "name": "Fire" }
    ]
  },

  "palette_library": {
    "source_ip": "192.168.1.10",
    "preview_signature": [],
    "palettes": [
      { "id": 0, "name": "Default" },
      { "id": 5, "name": "Forest" }
    ],
    "preview_data": {
      "0": [[255,0,0,0],[0,255,0,128],[0,0,255,255]],
      "5": [[0,128,0,0],[0,64,0,128]]
    }
  },

  "audio_path": "/path/to/music.mp3",
  "audio_trim": { "start": 0.0, "end": 180.5 },

  "mock_devices": [
    { "id": "mock:1", "name": "Stage Left" },
    { "id": "mock:2", "name": "Stage Right" }
  ],

  "tracks": [
    {
      "ip": "mock:1",
      "name": "Stage Left",
      "is_mock": true,
      "clips": [
        {
          "preset_id": 60,
          "name": "TMP Rainbow",
          "start": 0.0,
          "duration": 5.0,
          "transition": 0.5,
          "clip_type": "effect",
          "fx_id": 9,
          "palette_id": 5,
          "bake_snapshot": {
            "kind": "effect",
            "payload": {
              "n": "TMP Rainbow",
              "seg": [{
                "id": 0,
                "fx": 9,
                "pal": 5,
                "col": [[255, 0, 0], [0, 0, 0], [0, 0, 0]],
                "sx": 128,
                "ix": 128,
                "c1": 0,
                "c2": 0,
                "c3": 0,
                "o1": false,
                "o2": false,
                "o3": false,
                "tp": false,
                "rev": false
              }]
            },
            "files": []
          }
        },
        {
          "preset_id": 61,
          "name": "TMP Fire + Forest",
          "start": 5.5,
          "duration": 4.0,
          "transition": 0.3,
          "clip_type": "palette",
          "fx_id": 0,
          "palette_id": 5,
          "bake_snapshot": {
            "kind": "palette",
            "payload": {
              "n": "TMP Fire + Forest",
              "seg": [{
                "id": 0,
                "fx": 0,
                "pal": 5,
                "col": [[0, 255, 100]],
                "sx": 100,
                "ix": 200
              }]
            },
            "files": []
          }
        }
      ]
    },
    {
      "ip": "192.168.1.10",
      "name": "Center Stage",
      "is_mock": false,
      "clips": [
        {
          "preset_id": 3,
          "name": "Chase",
          "start": 1.0,
          "duration": 3.0,
          "transition": 0.0,
          "clip_type": "preset"
        }
      ]
    }
  ],

  "timing_marks": [0.0, 5.5, 9.5],

  "defaults": {
    "duration": 5.0,
    "transition": 0.5,
    "slot": 249,
    "loop": true
  }
}
```

---

## 3. Field Descriptions

### Root level

| Field | Type | Description |
|-------|------|-------------|
| `version` | int | Config format version. Currently `1`. Skip loading if > 1. |
| `preset_palette` | object | Cache of preset names loaded from a source device. Used for UI only. |
| `palette_library` | object | Cache of color palette names and preview data. Used for UI only. |
| `audio_path` | string \| null | Absolute local path to the audio file. May not exist on the Android device. |
| `audio_trim` | object \| null | `{start, end}` in seconds for audio trim range. |
| `mock_devices` | array | List of virtual devices defined in the session. |
| `tracks` | array | One entry per device (mock or real), containing the clip list. |
| `timing_marks` | array[float] | Beat/timing mark positions in seconds. UI-only. |
| `defaults` | object | Default values used in the editor. |

### `mock_devices[i]`

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique ID, always starts with `"mock:"`. E.g. `"mock:1"`, `"mock:2"`. |
| `name` | string | User-given label for this virtual device. E.g. `"Stage Left"`. |

### `tracks[i]`

| Field | Type | Description |
|-------|------|-------------|
| `ip` | string | Device IP (`"192.168.1.10"`) OR mock ID (`"mock:1"`). |
| `name` | string | Display name of this device/track. |
| `is_mock` | bool | `true` if this track belongs to a mock device. |
| `clips` | array | List of clips on this track. |

### `tracks[i].clips[j]`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `preset_id` | int | ✅ | Slot ID on the device. For mock clips this is a temporary virtual ID (60–240); for real-device clips it is an actual slot on the device. |
| `name` | string | ✅ | Display name. |
| `start` | float | ✅ | Start time in seconds from timeline origin. |
| `duration` | float | ✅ | Duration in seconds. |
| `transition` | float | ✅ | Transition/crossfade duration in seconds. |
| `clip_type` | string | optional | `"preset"`, `"effect"`, or `"palette"`. Defaults to `"preset"` if absent. |
| `fx_id` | int | optional | WLED effect ID. Present when `clip_type` is `"effect"` or `"palette"`. |
| `palette_id` | int | optional | WLED color palette ID. |
| `bake_snapshot` | object | optional | **Only present on mock-device clips.** Contains the full WLED preset payload to be uploaded to the real device. See below. |

### `bake_snapshot`

Present **only** when the clip lives on a mock device (`is_mock: true` / ip starts with `"mock:"`).  
This is the critical field that enables the Android plugin to bake the preset onto the real device.

```json
{
  "kind": "effect",
  "payload": {
    "n": "TMP name",
    "seg": [{
      "id": 0,
      "fx": 9,
      "pal": 5,
      "col": [[255, 0, 0], [0, 0, 0], [0, 0, 0]],
      "sx": 128,
      "ix": 128,
      "c1": 0,
      "c2": 0,
      "c3": 0,
      "o1": false,
      "o2": false,
      "o3": false,
      "tp": false,
      "rev": false
    }]
  },
  "files": [
    {
      "path": "/image.bmp",
      "size": 12288,
      "content_b64": "<base64-encoded binary>"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `kind` | `"effect"`, `"palette"`, or `"preset"`. Informational only; the `payload` is always a valid WLED preset JSON regardless of kind. |
| `payload` | **Ready-to-upload WLED preset object.** Write this directly into `presets.json` at the allocated slot. |
| `payload.n` | Preset name (max 40 chars). You may rename it. |
| `payload.seg` | List of segment configurations. Typically only segment 0. |
| `payload.seg[].fx` | Effect ID (0 = Solid). |
| `payload.seg[].pal` | Palette ID. |
| `payload.seg[].col` | Array of 3 colors: `[primary, secondary, tertiary]`, each `[R, G, B]` (0–255). |
| `payload.seg[].sx` | Speed (0–255). |
| `payload.seg[].ix` | Intensity (0–255). |
| `payload.seg[].c1..c3` | Custom effect parameters (0–255). |
| `payload.seg[].o1..o3` | Custom boolean options. |
| `payload.seg[].tp` | Palette as background texture (bool). |
| `payload.seg[].rev` | Reverse direction (bool). |
| `files` | Optional list of binary files (BMP/GIF images) required by the preset. Each has a `path` (upload target path on device) and `content_b64` (base64-encoded file content). Must be uploaded BEFORE writing `presets.json`. |

---

## 4. Reserved Slot Numbers

The following preset slot numbers are reserved by the system. **Never use them for baking clips.**

| Slot | Purpose |
|------|---------|
| `100` | Auto-save backup (AUTOSAVE_SLOT) |
| `241–247` | App-reserved range (do not use) |
| `248` | **TIMELINE_OFF** — silent placeholder for gaps between clips |
| `249` | **Default timeline playlist slot** (compiled output goes here) |
| `250` | Physical button / Magic preset |

> The TIMELINE_OFF preset payload (slot 248):
> ```json
> { "n": "TIMELINE_OFF", "seg": [{"id": 0, "fx": 0, "col": [[0, 0, 0]]}] }
> ```
> This makes LEDs appear off (black, solid color) without changing global `on`/`bri`.

---

## 5. Slot Allocation Algorithm

When baking a mock clip onto a real device, find the **first free slot** in range `[60, 240]`:

```
TEMP_PRESET_RANGE = [60, 240]
SKIP_SLOTS = {100, 248, 249, 250}

function findFreeSlot(existingPresets: Map<String, Object>): Int? {
    for (slot in 60..240) {
        if (slot in SKIP_SLOTS) continue
        val key = slot.toString()
        if (!existingPresets.containsKey(key) || existingPresets[key] == null) {
            return slot
        }
    }
    return null  // no free slot
}
```

`existingPresets` is the dict returned by `GET /presets.json` from the real device.  
Keys are **string** slot IDs (e.g. `"60"`, `"75"`).

---

## 6. WLED Device HTTP API

All communication with a real device uses plain HTTP on port 80.

### 6.1 Read current presets

```
GET http://{device_ip}/presets.json
Response: JSON object { "1": {...}, "2": {...}, "75": {...} }
```

Returns the complete presets map. Keys are string slot IDs.  
Returns `{}` if no presets exist.

### 6.2 Upload new presets.json

```
POST http://{device_ip}/upload
Content-Type: multipart/form-data
Field name: "data"
Filename: "presets.json"
Body: the full modified presets JSON (minified)
```

This **replaces** the entire `presets.json` on the device. Always read first, patch in-memory, then upload.

### 6.3 Upload binary files (BMP/GIF)

For each `bake_snapshot.files[i]` entry:

```
POST http://{device_ip}/upload
Content-Type: multipart/form-data
Field name: "data"
Filename: files[i].path  (e.g. "image.bmp")
Body: base64-decode(files[i].content_b64)
Content-Type of part: "image/bmp" or "image/gif"
```

> **Always upload files BEFORE uploading presets.json**, because the preset's `seg[].n` field may reference the file path.

### 6.4 Play a preset slot

```
POST http://{device_ip}/json/state
Content-Type: application/json
Body: { "on": true, "ps": <slot_id> }
```

### 6.5 Play a playlist (inline, not saved)

```
POST http://{device_ip}/json/state
Content-Type: application/json
Body: {
  "on": true,
  "seg": [{"id": 0, "frz": false}],
  "playlist": {
    "ps": [60, 248, 61],
    "dur": [50, 10, 40],
    "transition": [5, 0, 3],
    "repeat": 0,
    "end": 0,
    "r": 0
  }
}
```

> `dur` and `transition` are in **deciseconds** (1 s = 10 units).

### 6.6 Save playlist as preset (slot 249)

Read current `presets.json` → patch slot 249 → upload:

```json
{
  "249": {
    "playlist": {
      "ps": [60, 248, 61],
      "dur": [50, 10, 40],
      "transition": [5, 0, 3],
      "repeat": 0,
      "end": 0,
      "r": 0
    },
    "on": true,
    "n": "Timecode 249"
  }
}
```

---

## 7. Mock → Real Assignment Algorithm

This is the **core plugin workflow**. Execute in order:

### Step 1: Parse the JSON file

```
config = parse(timecode_config.json)
mockDevices   = config.mock_devices       // list of {id, name}
allTracks     = config.tracks             // list of track objects
mockTracks    = allTracks.filter(t => t.is_mock == true)
realTracks    = allTracks.filter(t => t.is_mock == false)
```

### Step 2: Show assignment UI

Display two lists to the user:
- **Left:** mock devices from `config.mock_devices`
- **Right:** real devices discovered on the local network (via mDNS or manual IP entry)

Let the user create a 1-to-1 mapping:  
`mapping: Map<mockId: String, realIp: String>`

Example:
```
"mock:1" → "192.168.1.10"
"mock:2" → "192.168.1.11"
```

> A mock device that is not mapped is simply skipped (its clips won't be included in the playlist).

### Step 3: For each mapped (mockId → realIp) pair

#### 3a. Collect clips that need baking

```
clipsToProcess = config.tracks
    .filter(t => t.ip == mockId)
    .flatMap(t => t.clips)
    .filter(c => c.bake_snapshot != null)
```

Clips without `bake_snapshot` (preset-type clips that reference an existing device preset) are handled differently — see step 3d.

#### 3b. Upload binary file dependencies (if any)

For each clip in `clipsToProcess`:
- For each entry in `clip.bake_snapshot.files`:
  - Decode `content_b64` from Base64 → raw bytes
  - `POST /upload` to `realIp` with the file bytes and filename = `entry.path`
  - Deduplicate: if two clips share a file path, upload it only once

#### 3c. Bake presets onto the real device

```
presets = GET http://{realIp}/presets.json  // existing presets map

newAssignments = Map<clipIndex, allocatedSlot>

for each clip in clipsToProcess:
    slot = findFreeSlot(presets)  // see Section 5
    if slot == null → error "No free slot, range 60-240 is full"
    
    payload = clip.bake_snapshot.payload   // ready-to-upload WLED preset
    presets[slot.toString()] = payload     // patch in-memory
    newAssignments[clip.index] = slot      // remember for playlist compile

POST http://{realIp}/upload  (presets.json with all patches)
```

#### 3d. Handle preset-type clips (no bake_snapshot)

Clips with `clip_type == "preset"` and no `bake_snapshot` reference a preset that is assumed to **already exist** on some device. If the original `ip` is the same as `realIp`, keep `preset_id` as-is. If not (cross-device mapping), you may warn the user or skip.

### Step 4: Compile the playlist for each real device

After baking, build the WLED playlist using the **assigned slot IDs**:

```
timeline_seconds = max(clip.start + clip.duration for all clips in all real tracks)
GAP_TOLERANCE = 0.05  // seconds

for each realIp in mapping.values():
    clips = all clips assigned to realIp, sorted by start time
    entries = []
    last_end = 0.0

    for each clip:
        gap = clip.start - last_end
        if gap > GAP_TOLERANCE:
            entries.append({ps: 248, dur: gap, trans: 0.0})  // OFF placeholder
        
        slot = newAssignments.get(clip) ?? clip.preset_id
        entries.append({ps: slot, dur: clip.duration, trans: clip.transition})
        last_end = max(last_end, clip.start + clip.duration)

    // Trailing OFF to sync with the longest track
    trailing = timeline_seconds - last_end
    if trailing > GAP_TOLERANCE:
        entries.append({ps: 248, dur: trailing, trans: 0.0})

    // Convert to WLED playlist format (deciseconds)
    playlist = {
        "ps": entries.map(e => e.ps),
        "dur": entries.map(e => max(1, round(e.dur * 10))),
        "transition": entries.map(e => max(0, round(e.trans * 10))),
        "repeat": config.defaults.loop ? 0 : 1,
        "end": 0,
        "r": 0
    }
```

### Step 5: Upload TIMELINE_OFF preset (slot 248) if needed

If any playlist contains `248` in its `ps` array:

```
presets = GET http://{realIp}/presets.json
presets["248"] = {
    "n": "TIMELINE_OFF",
    "seg": [{"id": 0, "fx": 0, "col": [[0, 0, 0]]}]
}
POST http://{realIp}/upload  (presets.json)
```

> This can be merged into the bake upload in Step 3c to save a round-trip.

### Step 6: Upload playlist to slot 249

```
presets = GET http://{realIp}/presets.json   // fresh read (or reuse patched copy)
presets["249"] = {
    "playlist": playlist,  // from Step 4
    "on": true,
    "n": "Timecode 249"
}
POST http://{realIp}/upload  (presets.json)
```

### Step 7: Play

To start playback on all assigned real devices:

```
for each realIp:
    POST http://{realIp}/json/state
    Body: { "on": true, "ps": 249 }
```

---

## 8. Complete Upload Sequence (Per Device)

```
┌─────────────────────────────────────────────────────────┐
│  For each (mockId → realIp) mapping:                    │
│                                                         │
│  1. Upload BMP/GIF files (bake_snapshot.files)          │
│     POST /upload  (for each unique file)                │
│                                                         │
│  2. GET /presets.json  → existingPresets                │
│                                                         │
│  3. For each mock clip with bake_snapshot:              │
│     a. findFreeSlot(existingPresets)                    │
│     b. existingPresets[slot] = bake_snapshot.payload    │
│     c. remember mapping: clip → slot                    │
│                                                         │
│  4. existingPresets["248"] = TIMELINE_OFF payload       │
│     (only if any playlist will contain ps=248)          │
│                                                         │
│  5. existingPresets["249"] = compiled playlist          │
│                                                         │
│  6. POST /upload  presets.json  (single upload)         │
│                                                         │
│  7. POST /json/state  {"on":true, "ps":249}  → play     │
└─────────────────────────────────────────────────────────┘
```

> Steps 3–5 can be merged into a single upload. Read once → patch all → write once.

---

## 9. Error Handling

| Situation | Recommended Action |
|-----------|-------------------|
| Device unreachable | Show error per device; continue with others |
| Slot range 60–240 full | Warn user; offer to clear old baked presets before retrying |
| File upload fails | Skip clips that depend on that file; report to user |
| `bake_snapshot` is `null` for a mock clip | Skip baking for that clip; if `clip_type == "preset"`, assume preset already on device |
| `preset_id <= 0` | Treat as an OFF placeholder (do not add to playlist as a real clip) |
| Device returns non-JSON or error on `/presets.json` | Abort for that device; report error |

---

## 10. Identifying Mock vs Real Tracks

```kotlin
fun isMockIp(ip: String): Boolean = ip.startsWith("mock:")
```

Mock IPs always start with `"mock:"` followed by an integer counter (e.g. `"mock:1"`, `"mock:42"`).  
Real device IPs are standard IPv4 addresses (e.g. `"192.168.1.10"`).

---

## 11. `defaults` Object

```json
"defaults": {
  "duration": 5.0,
  "transition": 0.5,
  "slot": 249,
  "loop": true
}
```

| Field | Meaning |
|-------|---------|
| `duration` | Default clip duration (seconds) — UI hint only |
| `transition` | Default crossfade (seconds) — UI hint only |
| `slot` | Target playlist slot (almost always `249`) |
| `loop` | If `true`, playlist repeats continuously (`repeat: 0`); if `false`, plays once (`repeat: 1`) |

---

## 12. Minimal Kotlin Example Sketch

```kotlin
data class BakeSnapshot(
    val kind: String,
    val payload: Map<String, Any>,
    val files: List<FileEntry>
)

data class FileEntry(
    val path: String,
    val size: Int?,
    val contentB64: String?
)

data class Clip(
    val presetId: Int,
    val name: String,
    val start: Double,
    val duration: Double,
    val transition: Double,
    val clipType: String = "preset",
    val fxId: Int? = null,
    val paletteId: Int? = null,
    val bakeSnapshot: BakeSnapshot? = null
)

data class Track(
    val ip: String,
    val name: String,
    val isMock: Boolean,
    val clips: List<Clip>
)

data class MockDevice(val id: String, val name: String)

data class TimecodeConfig(
    val version: Int,
    val mockDevices: List<MockDevice>,
    val tracks: List<Track>,
    val defaults: Map<String, Any>
)

// Reserved slots — never allocate these
val SKIP_SLOTS = setOf(100, 248, 249, 250)
val TEMP_RANGE = 60..240

fun findFreeSlot(presets: Map<String, Any>): Int? {
    for (slot in TEMP_RANGE) {
        if (slot in SKIP_SLOTS) continue
        if (!presets.containsKey(slot.toString())) return slot
    }
    return null
}

fun buildPlaylist(
    clips: List<Clip>,
    slotMap: Map<Int, Int>,   // oldPresetId -> newSlot
    timelineSeconds: Double,
    loop: Boolean
): Map<String, Any> {
    val GAP = 0.05
    val OFF = 248
    val entries = mutableListOf<Triple<Int, Double, Double>>()  // (ps, dur, trans)
    var lastEnd = 0.0

    for (clip in clips.sortedBy { it.start }) {
        val gap = clip.start - lastEnd
        if (gap > GAP) entries += Triple(OFF, gap, 0.0)
        val slot = slotMap[clip.presetId] ?: clip.presetId
        entries += Triple(slot, clip.duration, clip.transition)
        lastEnd = maxOf(lastEnd, clip.start + clip.duration)
    }
    val trailing = timelineSeconds - lastEnd
    if (trailing > GAP) entries += Triple(OFF, trailing, 0.0)

    return mapOf(
        "ps" to entries.map { it.first },
        "dur" to entries.map { maxOf(1, (it.second * 10).roundToInt()) },
        "transition" to entries.map { maxOf(0, (it.third * 10).roundToInt()) },
        "repeat" to if (loop) 0 else 1,
        "end" to 0,
        "r" to 0
    )
}
```

---

## 13. Summary Checklist for Android Plugin

- [ ] Parse `timecode_config.json` (version check: warn if version > 1)
- [ ] Extract `mock_devices` list for the assignment UI
- [ ] Discover real WLED devices (mDNS `_wled._tcp` or manual IP)
- [ ] Let user map each mock device to a real device IP
- [ ] For each mapped mock → real pair:
  - [ ] Upload `bake_snapshot.files` (base64-decode → POST /upload, BMP/GIF)
  - [ ] GET /presets.json from real device
  - [ ] Allocate free slots (60–240, skip 100/248/249/250) for each clip with `bake_snapshot`
  - [ ] Patch presets in-memory (slot → payload)
  - [ ] Patch slot 248 with TIMELINE_OFF if any playlist gap exists
  - [ ] Compile playlist from clip timings + slot assignments
  - [ ] Patch slot 249 with compiled playlist
  - [ ] POST /upload the merged presets.json (single upload)
- [ ] POST /json/state `{"on":true,"ps":249}` to start playback on each device

---

*Document generated from HSL Tool v3.4.3 source — `ui/timeline_tab.py`, config format version 1.*
