# Tracks and multi-part courses: summary

## 1. Firebase fields

**`courses/{programme}_{code}`**

| field | meaning |
|---|---|
| `programme`, `code`, `name` | e.g. CS240, ICT606, "ICT in Industries" |
| `slotType` | `CORE`, `UNIVERSITY` or `ELECTIVE` |
| `creditHours` | number, left out when unknown |
| `isSulam` | `true` only for the SULAM course (ICT652) |
| `parts` | every part the course is taught in, `{"5": true, "6": true}` |
| `part` | lowest of `parts`, kept so older screens still work |
| `tracks` | only on track electives: track ID → the part that track takes it in |

```json
"CS240_ICT606": {
  "programme": "CS240", "code": "ICT606", "name": "ICT in Industries",
  "slotType": "ELECTIVE", "creditHours": 3, "isSulam": false,
  "part": 5, "parts": { "5": true, "6": true },
  "tracks": { "CREATIVE_IT": 5, "BIG_DATA": 5, "IT_INFRASTRUCTURE": 6 }
}
```

**`groups/{groupCode}`**: `programme`, `part`, `letter`, and `track` (short ID) when the whole group follows one track.

```json
"CDCS2306A": { "programme": "CS230", "part": 6, "letter": "A", "track": "BIG_DATA" }
```

**`programmes/{code}`** (new fields): `tracks` (ID → label) and `trackStartPart` (the first part with tracks).

```json
"CS259": { "...": "...", "tracks": { "BIG_DATA": "Big Data", "INTELLIGENT_SYSTEMS": "Intelligent Systems" },
           "trackStartPart": 4 }
```

**`students/{uid}`** (new field): `track`, the short ID the student picked, e.g. `"CREATIVE_IT"`. Not stored when the group sets the track.

## 2. How a student gets a track

Both ways, depending on the group:

- **From the group (automatic, locked):** CS230 Part 6 (A/B Big Data, C/D Soft Computing) and CS259 Parts 4–6 (A–C Big Data, D–F Intelligent Systems).
- **Chosen by the student:** every other group in a part with tracks, i.e. CS240 Parts 5–6 and CS230 Part 5. They pick it at sign-up and can change it later in Profile.

Tracks start in Part 5 (CS230, CS240) or Part 4 (CS259). CS255, CS264 and CS266 have no tracks.

## 3. Screens that changed

- **Registration:** a **Track** dropdown appears under Part / Group only when the chosen part has tracks. If the group sets the track, the field is filled in and locked with "Set by your group." Otherwise it is required, with "You can change this later in your profile."
- **Profile (student):** a **My track** row from the track start part on. It shows "Big Data (set by your group)" with no chevron when locked, otherwise it is tappable and opens "Choose your track".
- **Add Teaching Class (lecturer):**
  - The order is now Programme → Part → Groups → Course.
  - The course list shows CORE, UNIVERSITY and ELECTIVE courses whose `parts` include the chosen part, so CS259 ACC407 appears in Parts 2, 3 and 5.
  - When the ticked groups follow one track, that track's electives move to the top.
  - New chip: "Elective" or "Elective · Big Data" next to the existing "SULAM course" chip.
- **Edit teaching class:** the part now comes from the group, not the course.
- My Attendance and the other student screens are unchanged.

## 4. Rule changes (`database.rules.json`)

- **Teaching classes:** before, a course could only be added to a group in the same part (`course.part == group.part`). Now the group's part only has to be in the course's `parts` list. The same-programme check is unchanged.
- **`students/{uid}/track` (new):** only the student can write it, and only after verifying their email (or as part of sign-up). It must be one of their programme's tracks. It is refused when their group already has a track, or when their part is before `trackStartPart`.

## 5. Not built, or built differently from the prompt

- The seed builder is `tools/build_seed.js`, not `build_seed.py`, because the project already had a JS builder.
- `tracks` stores the part per track (`{"BIG_DATA": 5}`), not `{"TRACK:BIG_DATA": true}`, and groups use the short ID. This was changed on request so a course like ICT606 can sit in different parts for different tracks.
- **Added, not in the prompt:**
  - `programmes/{code}/tracks` labels, for the chips.
  - `trackStartPart`, so sign-up and the rules don't hardcode CS259.
  - Track choice at sign-up.
  - The `students/{uid}/track` rule.
  - Groups moved above Course.
- `CLAUDE.md` and `SCHEMA.md` were written during the work, then deleted to keep the project folder clean. Section 6 records what `SCHEMA.md` said.
- **Tested on an emulator, against a local copy of the database** (the live database is not imported yet):
  - **Registration works:** CS240 Part 5 lists its three tracks and the pick is kept.
  - **Group tracks lock:** CDCS2306A fills in "Big Data", CDCS2306C fills in "Soft Computing", and the field can't be changed.
  - **Look:** the locked field doesn't look greyed out, because the app's dropdown style doesn't dim disabled fields.
  - **Not tried on the device:** Add Teaching Class and the Profile row.

  The 98 rules tests and 38 seed-builder tests pass.

## 6. Doc changes, before → after

**`SCHEMA.md`** (now deleted)

| before | after |
|---|---|
| "Give all of them to Claude Code in Android Studio" | Says `tools/build_seed.js` builds the nodes from the CSVs |
| `elective_options.csv` had a `partSource` column ("where that part came from") | Column removed |
| `groups.csv` had a `source` column | Column removed, only `notes` |
| "the official registration list also splits Part 4" (CS259) | "CS259 also splits Part 4 by track" |
| Note about the removed `track_requirements.csv` | Removed |

Also removed:
- The source citations in the CSV notes (registration lists, slides, study plans, AIMS, names).
- The `partSource` and `source` columns from the CSVs.

**`CLAUDE_CODE_Programme_Data.md` (the step list, deleted once all steps were done)**

| before | after |
|---|---|
| `tools/build_seed.py` | `tools/build_seed.js` |
| `tracks: { "TRACK:BIG_DATA": true }` | `tracks: { "BIG_DATA": 5, ... }`, part per track from `suggestedPart`, no `tracks` on non-track courses |
| `groups/{id}/track` = the `optionGroup` value | Short ID, e.g. `"BIG_DATA"` |
| Validation list | Adds "every `tracks` value is also in that course's `parts` map" |
| Step 3, track electives first | Adds the lookup rule: `tracks/{trackId} == part`, never the part from the course alone |

The step list does not mention `trackStartPart` or the track at sign-up. Both were added later in the session.
