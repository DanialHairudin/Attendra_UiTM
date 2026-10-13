#!/usr/bin/env node
/*
 * Rebuild the programmes / courses / groups part of the Attendra seed from
 * seed/programme_structure.csv.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const LETTERS = ['A', 'B', 'C'];
const CODE_PATTERN = /^[A-Z]{3}[0-9]{3}$/;

/**
 * 'Fundamentals of Database Design and Development' → 'FUNDAMENTALS_OF_DATABASE_DESIGN_AND_DEVE'
 * (the rule the current seed uses for TBC course IDs: max 40 characters).
 */
function slug(name) {
  return name.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 40);
}

function isTbc(code) {
  return !code || code.trim().toUpperCase() === 'TBC';
}

function norm(name) {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
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

/** The CSV rows as {line, row} objects; line numbers as Excel shows them (header = line 1). */
function readCsv(file) {
  const table = parseCsv(fs.readFileSync(file, 'utf8'));
  const header = (table[0] || []).map(h => h.trim());
  const need = ['programme', 'part', 'courseCode', 'courseName', 'isSulam'];
  const missing = need.filter(c => !header.includes(c));
  if (missing.length) throw new Error(`${path.basename(file)} is missing the column(s): ${missing.join(', ')}`);
  const rows = [];
  table.slice(1).forEach((cells, i) => {
    if (!cells.some(v => (v || '').trim())) return; // Skip empty lines
    const row = {};
    header.forEach((h, j) => { row[h] = cells[j] || ''; });
    rows.push({ line: i + 2, row });
  });
  return rows;
}

function build(seed, rows, errors, warnings) {
  const programmes = seed.programmes || {};
  const oldCourses = seed.courses || {};

  // Lookups to keep existing course IDs stable.
  const byCode = new Map();
  const byName = new Map();
  for (const [cid, c] of Object.entries(oldCourses)) {
    if (!isTbc(c.code)) byCode.set(`${c.programme}|${c.code.toUpperCase()}`, cid);
    byName.set(`${c.programme}|${Number(c.part)}|${norm(c.name)}`, cid);
  }

  const courses = {};
  const sulam = {};          // Programme → [{code, part, line}]
  const codesSeen = new Map(); // programme|code → line
  const usedIds = new Set();

  for (const { line, row } of rows) {
    const prog = (row.programme || '').trim().toUpperCase();
    let code = (row.courseCode || '').trim().toUpperCase();
    const name = (row.courseName || '').trim();
    const flag = (row.isSulam || '').trim().toUpperCase();
    const where = `line ${line} (${prog} ${code || 'no code'} ${name.slice(0, 40)})`;

    if (!programmes[prog]) { errors.push(`${where}: programme "${prog}" is not in seed programmes`); continue; }
    const partText = (row.part || '').trim();
    const part = Number(partText);
    if (!/^[0-9]+$/.test(partText)) { errors.push(`${where}: part must be a number`); continue; }
    const parts = Number(programmes[prog].parts || 6);
    if (part < 1 || part > parts) { errors.push(`${where}: part ${part} is outside 1-${parts}`); continue; }
    if (!name) { errors.push(`${where}: courseName is empty`); continue; }
    if (flag !== 'TRUE' && flag !== 'FALSE') { errors.push(`${where}: isSulam must be TRUE or FALSE, not "${flag}"`); continue; }

    if (isTbc(code)) {
      errors.push(`${where}: course code is still TBC`);
      code = 'TBC';
    } else {
      if (!CODE_PATTERN.test(code)) warnings.push(`${where}: code "${code}" does not look like ABC123`);
      const key = `${prog}|${code}`;
      if (codesSeen.has(key)) { errors.push(`${where}: code ${code} already used on line ${codesSeen.get(key)}`); continue; }
      codesSeen.set(key, line);
    }

    // Keep the old ID when we can match the course, so references survive.
    let cid = null;
    if (code !== 'TBC') cid = byCode.get(`${prog}|${code}`) || null;
    if (!cid) cid = byName.get(`${prog}|${part}|${norm(name)}`) || null;
    if (!cid || usedIds.has(cid)) cid = code !== 'TBC' ? `${prog}_${code}` : `${prog}_P${part}_${slug(name)}`;
    if (courses[cid]) { errors.push(`${where}: course ID ${cid} would be used twice`); continue; }
    usedIds.add(cid);
    courses[cid] = { programme: prog, part, name, code };

    if (flag === 'TRUE') (sulam[prog] = sulam[prog] || []).push({ code, part, line });
  }

  for (const [cid, c] of Object.entries(oldCourses)) {
    if (!courses[cid]) warnings.push(`course ${cid} (${c.code} ${c.name}) is no longer in the CSV`);
  }

  // Programmes: keep every field, set sulamCourse / sulamPart from the CSV.
  const newProgrammes = {};
  for (const [prog, p0] of Object.entries(programmes)) {
    const p = { ...p0 };
    const marked = sulam[prog] || [];
    if (marked.length === 1) {
      p.sulamCourse = marked[0].code;
      p.sulamPart = marked[0].part;
    } else {
      p.sulamCourse = 'TBC';
      if (!marked.length) errors.push(`${prog}: no course is marked isSulam = TRUE`);
      else errors.push(`${prog}: ${marked.length} courses are marked isSulam = TRUE (lines ${marked.map(m => m.line).join(', ')}); exactly one is needed`);
    }
    newProgrammes[prog] = p;
  }

  // Groups: A-C for every part of every programme, plus any extra ones that already exist.
  const groups = {};
  for (const [prog, p] of Object.entries(programmes)) {
    for (let part = 1; part <= Number(p.parts || 6); part++) {
      for (const letter of LETTERS) groups[`CD${prog}${part}${letter}`] = { programme: prog, part, letter };
    }
  }
  for (const [gid, g] of Object.entries(seed.groups || {})) {
    if (!groups[gid]) {
      groups[gid] = g;
      warnings.push(`group ${gid} is not one of A-C; kept as it is`);
    }
  }
  return { programmes: newProgrammes, courses, groups };
}

/** References in a live-database export that would break with the new data. */
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

function parseArgs(argv) {
  const args = {
    draft: false,
    backup: null,
    csv: path.join(ROOT, 'seed', 'programme_structure.csv'),
    seed: path.join(ROOT, 'seed', 'attendra_seed.json'),
    out: path.join(ROOT, 'seed'),
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--draft') args.draft = true;
    else if (['--backup', '--csv', '--seed', '--out'].includes(a) && argv[i + 1]) args[a.slice(2)] = path.resolve(argv[++i]);
    else if (a === '--help' || a === '-h') { args.help = true; }
    else throw new Error(`unknown option ${a} (see --help)`);
  }
  return args;
}

function main(argv) {
  const args = parseArgs(argv);
  if (args.help) {
    console.log('node tools/build_seed.js [--draft] [--backup export.json] [--csv file] [--seed file] [--out dir]');
    return 0;
  }
  const seed = JSON.parse(fs.readFileSync(args.seed, 'utf8'));
  const rows = readCsv(args.csv);

  const errors = [];
  const warnings = [];
  const built = build(seed, rows, errors, warnings);
  if (args.backup) checkBackup(args.backup, built.courses, built.groups, errors);

  warnings.forEach(w => console.log('WARNING ', w));
  errors.forEach(e => console.log('ERROR   ', e));
  console.log(`\n${rows.length} CSV rows -> ${Object.keys(built.courses).length} courses, ` +
    `${Object.keys(built.groups).length} groups, ${Object.keys(built.programmes).length} programmes. ` +
    `${errors.length} error(s), ${warnings.length} warning(s).`);

  if (errors.length && !args.draft) {
    console.log('Nothing written. Fix the errors above in the CSV, or use --draft to write anyway.');
    return 1;
  }

  // Keeps buildings, venues, clubs and any other node unchanged.
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

module.exports = { parseCsv, slug, build };
