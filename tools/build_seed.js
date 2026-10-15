#!/usr/bin/env node
/*
 * Rebuilds the programmes / courses / groups part of the Attendra seed from the three CSVs in
 * seed/ (programme_structure.csv, elective_options.csv, groups.csv). Everything else in the
 * seed (buildings, venues, clubs, ...) is copied as it is.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const LETTERS = ['A', 'B', 'C'];
const CODE_PATTERN = /^[A-Z]{3}[0-9]{3}$/;
const SLOT_TYPES = ['CORE', 'UNIVERSITY', 'ELECTIVE'];
const CODE_STATUSES = ['VERIFIED', 'MATCHED', 'LIKELY', 'TBC'];

// Tracks normally start in Part 5. CS259 is the odd one out: it already splits by track in Part 4.
const FIRST_TRACK_PART = { CS259: 4 };
const firstTrackPart = prog => FIRST_TRACK_PART[prog] || 5;

function isTbc(code) {
  return !code || code.trim().toUpperCase() === 'TBC';
}

/**
 * Parses CSV text the way Excel writes it: commas, "quoted, fields", "" for a quote inside quotes,
 * CRLF or LF line ends, and an optional UTF-8 BOM.
 */
function parseCsv(text) {
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (ch === '"') quoted = false;
      else field += ch;
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ',') {
      row.push(field); field = '';
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else {
      field += ch;
    }
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row); }
  return rows;
}

/**
 * Reads a CSV into {line, row} objects, with every value trimmed. Line numbers match what Excel
 * shows (the header is line 1), so error messages point at the right row.
 */
function readCsv(file, need) {
  const table = parseCsv(fs.readFileSync(file, 'utf8'));
  const header = (table[0] || []).map(h => h.trim());
  const missing = need.filter(c => !header.includes(c));
  if (missing.length) throw new Error(`${path.basename(file)} is missing the column(s): ${missing.join(', ')}`);
  const rows = [];
  table.slice(1).forEach((cells, i) => {
    if (!cells.some(v => (v || '').trim())) return; // blank line, nothing to do
    const row = {};
    header.forEach((h, j) => { row[h] = (cells[j] || '').trim(); });
    rows.push({ line: i + 2, row });
  });
  return rows;
}

const STRUCTURE_COLUMNS = ['programme', 'part', 'slotType', 'courseCode', 'courseName', 'creditHours', 'optionGroup', 'isSulam', 'codeStatus'];
const OPTION_COLUMNS = ['programme', 'optionGroup', 'groupLabel', 'courseCode', 'courseName', 'creditHours', 'suggestedPart', 'codeStatus'];
const GROUP_COLUMNS = ['programme', 'part', 'group', 'groupCode', 'optionGroup'];

/**
 * Turns the CSV rows into the three seed nodes. Problems go into `errors` (stop the build) or
 * `warnings` (worth a look, but fine to import).
 */
function build(seed, csv, errors, warnings) {
  const programmes = seed.programmes || {};
  const oldCourses = seed.courses || {};

  // Small helper so every message says which file and line it came from.
  const at = (file, line, row) =>
    `${file} line ${line} (${row.programme || '?'} ${row.courseCode || row.groupCode || ''})`.replace(/ \)$/, ')');

  // Checks the programme and part on a row; returns the part as a number, or null if it's bad.
  function checkPart(where, prog, partText, label = 'part') {
    if (!programmes[prog]) { errors.push(`${where}: programme "${prog}" is not in seed programmes`); return null; }
    if (!/^[0-9]+$/.test(partText)) { errors.push(`${where}: ${label} must be a number, not "${partText}"`); return null; }
    const part = Number(partText);
    const max = Number(programmes[prog].parts || 6);
    if (part < 1 || part > max) { errors.push(`${where}: ${label} ${part} is outside 1-${max}`); return null; }
    return part;
  }

  const courses = {};
  const names = new Map();      // code → {name, where}, so one code can't have two names
  const sulam = {};             // programme → [{code, part, where}]
  const tracks = {};            // programme → {BIG_DATA → "Big Data", ...}
  const pools = new Map();      // programme|optionGroup|part → Set of codes
  const slots = new Map();      // programme|optionGroup|part → [where, ...]

  // Adds one course row to courses/{programme}_{code}. If the course is already there (same
  // course in another part, or also in a track pool), we just merge the new part and track in.
  function addCourse(where, prog, part, row, extra) {
    const code = row.courseCode.toUpperCase();
    const name = row.courseName;
    if (!name) { errors.push(`${where}: courseName is empty`); return; }
    if (isTbc(code) || row.codeStatus.toUpperCase() === 'TBC') { errors.push(`${where}: course code is still TBC`); return; }
    if (!CODE_PATTERN.test(code)) warnings.push(`${where}: code "${code}" does not look like ABC123`);
    if (row.codeStatus && !CODE_STATUSES.includes(row.codeStatus.toUpperCase())) {
      warnings.push(`${where}: codeStatus "${row.codeStatus}" is not one of ${CODE_STATUSES.join(', ')}`);
    }

    const seen = names.get(code);
    if (!seen) names.set(code, { name, where });
    else if (seen.name !== name) errors.push(`${where}: ${code} is called "${name}" here but "${seen.name}" on ${seen.where}`);

    let credits = null;
    if (row.creditHours) {
      credits = Number(row.creditHours);
      if (!Number.isFinite(credits) || credits <= 0) { errors.push(`${where}: creditHours "${row.creditHours}" is not a number`); credits = null; }
    }

    const cid = `${prog}_${code}`;
    let c = courses[cid];
    if (!c) {
      c = courses[cid] = { programme: prog, code, name, part, parts: {}, slotType: extra.slotType, isSulam: false };
    } else if (extra.fromStructure && c.slotType !== extra.slotType) {
      // Structure rows are read before the pools, so whatever the structure says first wins.
      warnings.push(`${where}: ${code} is ${extra.slotType} here but ${c.slotType} in another part; kept ${c.slotType}`);
    }

    // The old single "part" field stays as the lowest part, so screens that still read it work.
    c.part = Math.min(c.part, part);
    c.parts[String(part)] = true;
    if (credits !== null) {
      if (c.creditHours !== undefined && c.creditHours !== credits) {
        warnings.push(`${where}: ${code} has ${credits} credit hours here but ${c.creditHours} elsewhere; kept ${c.creditHours}`);
      } else c.creditHours = credits;
    }
    if (extra.isSulam) c.isSulam = true;
    // The part a track takes this course in. It can differ per track (CS240 ICT606 is Part 5 in
    // Creative IT but Part 6 in IT Infrastructure), so never guess it from the course alone.
    if (extra.track) (c.tracks = c.tracks || {})[extra.track] = part;
  }

  // ------------------------------------------------------------ programme_structure.csv
  const slotKeys = new Map(); // programme|part|code → line, to catch the same course twice in one part
  for (const { line, row } of csv.structure) {
    const where = at('programme_structure.csv', line, row);
    const prog = row.programme.toUpperCase();
    const part = checkPart(where, prog, row.part);
    if (part === null) continue;
    const slotType = row.slotType.toUpperCase();
    const flag = row.isSulam.toUpperCase();
    const optionGroup = row.optionGroup;
    if (!SLOT_TYPES.includes(slotType)) { errors.push(`${where}: slotType must be ${SLOT_TYPES.join(', ')}, not "${row.slotType}"`); continue; }
    if (flag !== 'TRUE' && flag !== 'FALSE') { errors.push(`${where}: isSulam must be TRUE or FALSE, not "${row.isSulam}"`); continue; }

    if (row.courseCode.toUpperCase() === 'ELECTIVE') {
      // This is just an empty slot the student fills in later. It never becomes a course of its
      // own (it would end up as {programme}_ELECTIVE and clash), we only remember it for the
      // pool checks further down.
      if (!optionGroup) { errors.push(`${where}: an ELECTIVE slot needs an optionGroup (TRACK or a group in elective_options.csv)`); continue; }
      if (flag === 'TRUE') errors.push(`${where}: an ELECTIVE slot can't be the SULAM course`);
      const key = `${prog}|${optionGroup}|${part}`;
      if (!slots.has(key)) slots.set(key, []);
      slots.get(key).push(where);
      continue;
    }
    if (optionGroup) warnings.push(`${where}: has a real code, so optionGroup "${optionGroup}" is ignored`);

    const key = `${prog}|${part}|${row.courseCode.toUpperCase()}`;
    if (slotKeys.has(key)) { errors.push(`${where}: ${row.courseCode} is already in ${prog} Part ${part} on line ${slotKeys.get(key)}`); continue; }
    slotKeys.set(key, line);

    addCourse(where, prog, part, row, { slotType, fromStructure: true, isSulam: flag === 'TRUE' });
    if (flag === 'TRUE') (sulam[prog] = sulam[prog] || []).push({ code: row.courseCode.toUpperCase(), part, where });
  }

  // ------------------------------------------------------------ elective_options.csv
  const optionKeys = new Map(); // programme|optionGroup|code → line
  for (const { line, row } of csv.options) {
    const where = at('elective_options.csv', line, row);
    const prog = row.programme.toUpperCase();
    const part = checkPart(where, prog, row.suggestedPart, 'suggestedPart');
    if (part === null) continue;
    const group = row.optionGroup;
    if (!group) { errors.push(`${where}: optionGroup is empty`); continue; }
    const isTrack = group.startsWith('TRACK:');
    if (isTrack && !/^TRACK:[A-Z0-9_]+$/.test(group)) { errors.push(`${where}: track "${group}" should look like TRACK:SOME_NAME`); continue; }

    const key = `${prog}|${group}|${row.courseCode.toUpperCase()}`;
    if (optionKeys.has(key)) { errors.push(`${where}: ${row.courseCode} is already in ${prog} ${group} on line ${optionKeys.get(key)}`); continue; }
    optionKeys.set(key, line);

    // In the database a track is just its short ID ("BIG_DATA"), the "TRACK:" bit only lives in the CSV.
    const trackId = isTrack ? group.slice(6) : null;
    if (isTrack) {
      if (part < firstTrackPart(prog)) errors.push(`${where}: tracks start in Part ${firstTrackPart(prog)} for ${prog}, but this one is in Part ${part}`);
      const label = row.groupLabel || trackId;
      const known = (tracks[prog] = tracks[prog] || {});
      if (known[trackId] && known[trackId] !== label) warnings.push(`${where}: ${trackId} is labelled "${label}" here but "${known[trackId]}" before; kept "${known[trackId]}"`);
      else known[trackId] = label;
    }

    const poolKey = `${prog}|${trackId || group}|${part}`;
    if (!pools.has(poolKey)) pools.set(poolKey, new Set());
    pools.get(poolKey).add(row.courseCode.toUpperCase());

    addCourse(where, prog, part, row, { slotType: 'ELECTIVE', track: trackId });
  }

  // ------------------------------------------------------------ every slot can actually be filled
  // A TRACK slot needs enough courses in every one of that programme's tracks (nobody can take
  // the same course twice). Any other group just needs enough courses in its own pool.
  for (const [key, wheres] of slots) {
    const [prog, group, partText] = key.split('|');
    const part = Number(partText);
    const need = wheres.length;
    if (group === 'TRACK') {
      if (part < firstTrackPart(prog)) errors.push(`${wheres[0]}: tracks start in Part ${firstTrackPart(prog)} for ${prog}, but this slot is in Part ${part}`);
      const own = Object.keys(tracks[prog] || {});
      if (!own.length) { errors.push(`${wheres[0]}: TRACK slot, but ${prog} has no tracks in elective_options.csv`); continue; }
      for (const t of own) {
        const have = (pools.get(`${prog}|${t}|${part}`) || new Set()).size;
        if (have < need) errors.push(`${prog} Part ${part}: ${need} TRACK slot(s) but ${t} only has ${have} course(s) for Part ${part}`);
      }
    } else {
      const have = (pools.get(`${prog}|${group}|${part}`) || new Set()).size;
      if (have < need) errors.push(`${prog} Part ${part}: ${need} ${group} slot(s) but elective_options.csv only has ${have} course(s) for it`);
    }
  }

  // Every part in a course's tracks map has to be in its parts map too, otherwise the rules
  // (which only look at parts) would block a class the app happily offers.
  for (const [cid, c] of Object.entries(courses)) {
    for (const [t, part] of Object.entries(c.tracks || {})) {
      if (!c.parts[String(part)]) errors.push(`courses/${cid}: tracks/${t} is Part ${part}, but Part ${part} is not in its parts map`);
    }
  }

  // Old course IDs that disappear. Usually fine (mostly the old TBC-style IDs), but if a live
  // teaching class still points at one, run with --backup to find out. One line per programme
  // is plenty, otherwise this drowns out everything else.
  const gone = {};
  for (const [cid, c] of Object.entries(oldCourses)) {
    if (!courses[cid]) (gone[c.programme] = gone[c.programme] || []).push(cid);
  }
  for (const [prog, ids] of Object.entries(gone)) {
    warnings.push(`${prog}: ${ids.length} old course ID(s) are no longer in the CSVs (e.g. ${ids.slice(0, 2).join(', ')})`);
  }

  // ------------------------------------------------------------ programmes
  // Keep every field that's already there; just refresh the SULAM course and the track labels.
  const newProgrammes = {};
  for (const [prog, p0] of Object.entries(programmes)) {
    const p = { ...p0 };
    const marked = sulam[prog] || [];
    if (marked.length === 1) {
      p.sulamCourse = marked[0].code;
      p.sulamPart = marked[0].part;
    } else if (!marked.length) {
      errors.push(`${prog}: no course is marked isSulam = TRUE`);
    } else {
      errors.push(`${prog}: ${marked.length} courses are marked isSulam = TRUE (${marked.map(m => m.where).join('; ')}); exactly one is needed`);
    }
    if (tracks[prog]) {
      p.tracks = tracks[prog];
      // The first part where students split into tracks (5, or 4 for CS259). Lives here because
      // programmes can be read before login, so the sign-up form and the rules can both use it.
      p.trackStartPart = Math.min(...Object.values(courses)
        .filter(c => c.programme === prog && c.tracks)
        .flatMap(c => Object.values(c.tracks)));
    } else {
      delete p.tracks;
      delete p.trackStartPart;
    }
    newProgrammes[prog] = p;
  }

  // ------------------------------------------------------------ groups
  // Everyone gets A, B and C. groups.csv adds the extra letters and tells us which track a
  // group follows.
  const groups = {};
  for (const [prog, p] of Object.entries(programmes)) {
    for (let part = 1; part <= Number(p.parts || 6); part++) {
      for (const letter of LETTERS) groups[`CD${prog}${part}${letter}`] = { programme: prog, part, letter };
    }
  }
  const groupLines = new Map();
  for (const { line, row } of csv.groups) {
    const where = at('groups.csv', line, row);
    const prog = row.programme.toUpperCase();
    const part = checkPart(where, prog, row.part);
    if (part === null) continue;
    const letter = row.group.toUpperCase();
    const gid = row.groupCode.toUpperCase();
    if (!/^[A-Z]$/.test(letter)) { errors.push(`${where}: group should be a single letter, not "${row.group}"`); continue; }
    if (!gid) { errors.push(`${where}: groupCode is empty`); continue; }
    if (gid !== `CD${prog}${part}${letter}`) warnings.push(`${where}: groupCode ${gid} doesn't follow CD${prog}${part}${letter}; used as it is`);
    if (groupLines.has(gid)) { errors.push(`${where}: ${gid} is already on line ${groupLines.get(gid)}`); continue; }
    groupLines.set(gid, line);

    const g = { programme: prog, part, letter };
    const track = row.optionGroup.replace(/^TRACK:/, '');
    if (track) {
      if (!(tracks[prog] || {})[track]) errors.push(`${where}: ${row.optionGroup} is not one of ${prog}'s tracks in elective_options.csv`);
      else if (part < firstTrackPart(prog)) errors.push(`${where}: tracks start in Part ${firstTrackPart(prog)} for ${prog}, but this group is in Part ${part}`);
      else g.track = track;
    }
    groups[gid] = g;
  }
  // Groups someone added to the seed by hand that the CSV doesn't know about. Keep them.
  for (const [gid, g] of Object.entries(seed.groups || {})) {
    if (!groups[gid]) {
      groups[gid] = g;
      warnings.push(`group ${gid} is not in groups.csv and not one of A-C; kept as it is`);
    }
  }

  return { programmes: newProgrammes, courses, groups };
}

/** Looks through a live-database export for anything that would point at a course or group we no longer have. */
function checkBackup(file, courses, groups, errors) {
  const db = JSON.parse(fs.readFileSync(file, 'utf8'));
  for (const [lecturer, items] of Object.entries(db.teachingAssignments || {})) {
    for (const [key, a] of Object.entries(items || {})) {
      if (!courses[a.courseId]) errors.push(`teachingAssignments/${lecturer}/${key}: course ${a.courseId} would no longer exist`);
      if (!groups[a.groupId]) errors.push(`teachingAssignments/${lecturer}/${key}: group ${a.groupId} would no longer exist`);
    }
  }
  for (const [sid, s] of Object.entries(db.sessions || {})) {
    if (s.courseId && !courses[s.courseId]) errors.push(`sessions/${sid}: course ${s.courseId} would no longer exist`);
    for (const g of Object.keys(s.groups || {})) {
      if (!groups[g]) errors.push(`sessions/${sid}: group ${g} would no longer exist`);
    }
  }
  for (const [uid, st] of Object.entries(db.students || {})) {
    if (st.groupId && !groups[st.groupId]) errors.push(`students/${uid}: group ${st.groupId} would no longer exist`);
  }
}

/** Courses and groups per programme, for a quick sanity check at the end. */
function summary(built) {
  const lines = ['', 'programme  courses  groups  tracks'];
  for (const prog of Object.keys(built.programmes)) {
    const c = Object.values(built.courses).filter(x => x.programme === prog).length;
    const g = Object.values(built.groups).filter(x => x.programme === prog).length;
    const t = Object.values(built.programmes[prog].tracks || {}).join(', ') || '-';
    lines.push(`${prog.padEnd(10)} ${String(c).padStart(7)} ${String(g).padStart(7)}  ${t}`);
  }
  return lines.join('\n');
}

function parseArgs(argv) {
  const args = {
    draft: false,
    backup: null,
    structure: path.join(ROOT, 'seed', 'programme_structure.csv'),
    options: path.join(ROOT, 'seed', 'elective_options.csv'),
    groups: path.join(ROOT, 'seed', 'groups.csv'),
    seed: path.join(ROOT, 'seed', 'attendra_seed.json'),
    out: path.join(ROOT, 'seed'),
  };
  const withValue = ['--backup', '--structure', '--options', '--groups', '--seed', '--out'];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--draft') args.draft = true;
    else if (withValue.includes(a) && argv[i + 1]) args[a.slice(2)] = path.resolve(argv[++i]);
    else if (a === '--help' || a === '-h') args.help = true;
    else throw new Error(`unknown option ${a} (see --help)`);
  }
  return args;
}

function main(argv) {
  const args = parseArgs(argv);
  if (args.help) {
    console.log('node tools/build_seed.js [--draft] [--backup export.json] [--structure file] [--options file] [--groups file] [--seed file] [--out dir]');
    return 0;
  }
  const seed = JSON.parse(fs.readFileSync(args.seed, 'utf8'));
  const csv = {
    structure: readCsv(args.structure, STRUCTURE_COLUMNS),
    options: readCsv(args.options, OPTION_COLUMNS),
    groups: readCsv(args.groups, GROUP_COLUMNS),
  };

  const errors = [];
  const warnings = [];
  const built = build(seed, csv, errors, warnings);
  if (args.backup) checkBackup(args.backup, built.courses, built.groups, errors);

  warnings.forEach(w => console.log('WARNING ', w));
  errors.forEach(e => console.log('ERROR   ', e));
  console.log(summary(built));
  console.log(`\n${csv.structure.length} + ${csv.options.length} + ${csv.groups.length} CSV rows -> ` +
    `${Object.keys(built.courses).length} courses, ${Object.keys(built.groups).length} groups, ` +
    `${Object.keys(built.programmes).length} programmes. ${errors.length} error(s), ${warnings.length} warning(s).`);

  if (errors.length && !args.draft) {
    console.log('Nothing written. Fix the errors above in the CSVs, or use --draft to write anyway.');
    return 1;
  }

  // Only these three nodes change; buildings, venues, clubs and the rest go through untouched.
  const whole = { ...seed, programmes: built.programmes, courses: built.courses, groups: built.groups };
  fs.mkdirSync(args.out, { recursive: true });
  const write = (name, data) => {
    const file = path.join(args.out, name);
    fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
    console.log(`  wrote ${path.relative(ROOT, file)}`);
  };
  write('attendra_seed.built.json', whole);
  write('import_programmes.json', built.programmes);
  write('import_courses.json', built.courses);
  write('import_groups.json', built.groups);
  if (errors.length) console.log('DRAFT: written with errors. Do not import these files into the live database yet.');
  return 0;
}

if (require.main === module) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (e) {
    console.error('ERROR   ', e.message);
    process.exitCode = 1;
  }
}

module.exports = { parseCsv, build, readCsv };
