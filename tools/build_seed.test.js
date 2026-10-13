#!/usr/bin/env node
/* Tests for tools/build_seed.js. */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { parseCsv, slug } = require('./build_seed');

const ROOT = path.resolve(__dirname, '..');
const SEED = path.join(ROOT, 'seed', 'attendra_seed.json');
const CSV = path.join(ROOT, 'seed', 'programme_structure.csv');
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'attendra-seed-test-'));
const seedTextBefore = fs.readFileSync(SEED, 'utf8');
const seed = JSON.parse(seedTextBefore);
const csvText = fs.readFileSync(CSV, 'utf8');
// Expected counts come from the CSV itself, so the tests keep passing as it gets filled in.
const csvRows = parseCsv(csvText).slice(1).filter(r => r.some(v => v));
const tbcCount = csvRows.filter(r => (r[2] || '').trim().toUpperCase() === 'TBC').length;
const sulamProgs = new Set(csvRows.filter(r => (r[4] || '').trim().toUpperCase() === 'TRUE').map(r => r[0]));
const noSulam = Object.keys(seed.programmes).filter(p => !sulamProgs.has(p));

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass++; console.log(`PASS  ${name}`); }
  else { fail++; console.log(`FAIL  ${name}${detail ? ': ' + detail : ''}`); }
}

/** Runs the builder on a CSV text; returns {code, out, dir}. */
let n = 0;
function run(csv, extra = []) {
  const dir = path.join(TMP, `case${++n}`);
  fs.mkdirSync(dir);
  const csvFile = path.join(dir, 'in.csv');
  fs.writeFileSync(csvFile, csv);
  try {
    const out = execFileSync(process.execPath,
      [path.join(__dirname, 'build_seed.js'), '--csv', csvFile, '--seed', SEED, '--out', dir, ...extra],
      { encoding: 'utf8' });
    return { code: 0, out, dir };
  } catch (e) {
    return { code: e.status, out: e.stdout, dir };
  }
}
const read = (dir, f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
const written = dir => fs.existsSync(path.join(dir, 'import_courses.json'));

/** The real CSV with every TBC replaced by a made-up code and one SULAM row per programme. */
function completeCsv() {
  const rows = parseCsv(csvText).filter(r => r.some(v => v));
  let k = 100;
  const sulamDone = new Set(sulamProgs);
  return rows.map((r, i) => {
    if (i === 0) return r;
    const [prog, part] = r;
    if (r[2] === 'TBC') r[2] = `ZZT${k++}`;
    if (!sulamDone.has(prog) && part === '6') { r[4] = 'TRUE'; sulamDone.add(prog); }
    return r;
  }).map(r => r.map(v => /[",]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v).join(',')).join('\r\n') + '\r\n';
}

// ---------------------------------------------------------------- the CSV reader
const parsed = parseCsv('﻿a,b\r\n"x, y","say ""hi"""\r\n');
check('CSV: BOM, CRLF, quoted comma and "" quotes', parsed[1][0] === 'x, y' && parsed[1][1] === 'say "hi"' && parsed[0][0] === 'a');
check('slug rule matches the seed', slug('Fundamentals of Database Design and Development') === 'FUNDAMENTALS_OF_DATABASE_DESIGN_AND_DEVE');

// ---------------------------------------------------------------- the current CSV
let r = run(csvText);
check('current CSV, strict: stops (TBC codes, missing SULAM)', r.code === 1 && !written(r.dir));
check(`current CSV, strict: reports ${tbcCount} TBC codes`, (r.out.match(/still TBC/g) || []).length === tbcCount);
check(`current CSV, strict: reports ${noSulam.length} programmes without SULAM`,
  (r.out.match(/no course is marked isSulam/g) || []).length === noSulam.length);

r = run(csvText, ['--draft']);
const courses = read(r.dir, 'import_courses.json');
const groups = read(r.dir, 'import_groups.json');
const programmes = read(r.dir, 'import_programmes.json');
const whole = read(r.dir, 'attendra_seed.built.json');
check('current CSV, draft: writes the files', r.code === 0 && written(r.dir));
check('round trip: courses identical to the seed (IDs and values)', JSON.stringify(courses) === JSON.stringify(seed.courses));
check('round trip: groups identical to the seed (A-C everywhere)', JSON.stringify(groups) === JSON.stringify(seed.groups));
check('CS240: sulamCourse ICT652, sulamPart 6', programmes.CS240.sulamCourse === 'ICT652' && programmes.CS240.sulamPart === 6);
check('programmes with no SULAM row: sulamCourse TBC', noSulam.every(p => programmes[p].sulamCourse === 'TBC'));
check('programmes with a SULAM row: sulamCourse is a code or a course ID',
  [...sulamProgs].every(p => programmes[p].sulamCourse !== 'TBC'
    && (/^[A-Z]{3}\d{3}$/.test(programmes[p].sulamCourse) || courses[programmes[p].sulamCourse])));

// A SULAM course whose code is still TBC: sulamCourse is its course ID.
const espi = 'CS255,6,TBC,"Ethical, Social and Professional Issues in ICT",';
r = run(csvText.replace(espi + 'FALSE', espi + 'TRUE'), ['--draft']);
check('SULAM course with TBC code: sulamCourse is its course ID',
  read(r.dir, 'import_programmes.json').CS255.sulamCourse === 'CS255_P6_' + slug('Ethical, Social and Professional Issues in ICT'));
check('buildings, venues and clubs copied unchanged',
  ['buildings', 'venues', 'clubs'].every(k => JSON.stringify(whole[k]) === JSON.stringify(seed[k])));

// ---------------------------------------------------------------- filling in a code
// Uses the first course that still has a TBC code, so the test does not depend on one programme.
const toLine = r => r.map(v => /[",]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v).join(',');
const tbc = csvRows.find(r => (r[2] || '').trim().toUpperCase() === 'TBC');
if (tbc) {
  const [tp, tpart, , tname, tflag] = tbc;
  const oldId = `${tp}_P${tpart}_${slug(tname)}`;
  r = run(csvText.replace(toLine(tbc), toLine([tp, tpart, 'ZZQ999', tname, tflag])), ['--draft']);
  let c = read(r.dir, 'import_courses.json');
  check('TBC code filled in: the course keeps its old ID', c[oldId] && c[oldId].code === 'ZZQ999' && !c[tp + '_ZZQ999']);

  r = run(csvText.replace(toLine(tbc), toLine([tp, tpart, 'ZZQ999', tname + ' X', tflag])), ['--draft']);
  c = read(r.dir, 'import_courses.json');
  check('code AND name changed: new ID {programme}_{code} + warning',
    c[tp + '_ZZQ999'] && !c[oldId] && new RegExp(oldId + ' .*no longer in the CSV').test(r.out));
}

// ---------------------------------------------------------------- errors
const ict600 = 'CS240,5,ICT600,Web Technology and Application,FALSE';
r = run(csvText.replace(ict600, 'CS240,5,ICT600,Web Technology and Application,TRUE'));
check('two SULAM courses in one programme: ERROR', r.code === 1 && /CS240: 2 courses are marked isSulam/.test(r.out));
r = run(csvText.replace(ict600, 'CS240,5,ICT602,Web Technology and Application,FALSE'));
check('duplicate code in one programme: ERROR', /code ICT602 already used on line/.test(r.out));
r = run(csvText.replace(ict600, 'CS240,9,ICT600,Web Technology and Application,FALSE'));
check('part outside 1-6: ERROR', /part 9 is outside 1-6/.test(r.out));
r = run(csvText.replace(ict600, 'CS999,5,ICT600,Web Technology and Application,FALSE'));
check('unknown programme: ERROR', /programme "CS999" is not in seed programmes/.test(r.out));
r = run(csvText.replace(ict600, 'CS240,5,ICT600,Web Technology and Application,yes'));
check('isSulam not TRUE/FALSE: ERROR', /isSulam must be TRUE or FALSE/.test(r.out));
r = run(csvText.replace(ict600, 'CS240,5,ICT600,,FALSE'));
check('empty course name: ERROR', /courseName is empty/.test(r.out));
r = run(csvText.replace(ict600, 'CS240,5,IT-600,Web Technology and Application,FALSE'), ['--draft']);
check('odd-looking code: WARNING only', r.code === 0 && /code "IT-600" does not look like ABC123/.test(r.out));
r = run('programme,part,courseCode\nCS240,1,CSC415\n');
check('missing column: clear error', r.code === 1);

// ---------------------------------------------------------------- a complete CSV
const full = completeCsv();
r = run(full);
check('complete CSV, strict: passes and writes', r.code === 0 && written(r.dir), r.out.split('\n').slice(-3).join(' '));
if (written(r.dir)) {
  const p = read(r.dir, 'import_programmes.json');
  check('complete CSV: every programme has a sulamCourse', Object.values(p).every(x => x.sulamCourse && x.sulamCourse !== 'TBC'));
}

// ---------------------------------------------------------------- --backup check
const backup = path.join(TMP, 'backup.json');
fs.writeFileSync(backup, JSON.stringify({
  teachingAssignments: { zzLec: {
    CS240_ICT652_CDCS2406C: { courseId: 'CS240_ICT652', groupId: 'CDCS2406C' },
    ...(tbc ? { zzRef: { courseId: `${tbc[0]}_P${tbc[1]}_${slug(tbc[3])}`, groupId: `CD${tbc[0]}${tbc[1]}A` } } : {}),
  } },
  sessions: { zzS1: { courseId: 'CS240_ICT652', groups: { CDCS2406A: true } } },
  students: { zzStu: { groupId: 'CDCS2406B' } },
}));
r = run(full, ['--backup', backup]);
check('backup: nothing broken → passes', r.code === 0, r.out.split('\n').filter(l => l.startsWith('ERROR')).join(' | '));
if (tbc) {
  // Rename that course in the complete CSV: the teaching class pointing at it must be reported.
  const fullRows = parseCsv(full);
  const i = fullRows.findIndex(x => x[0] === tbc[0] && x[1] === tbc[1] && x[3] === tbc[3]);
  fullRows[i] = [tbc[0], tbc[1], 'ZZQ998', tbc[3] + ' X', fullRows[i][4]];
  r = run(fullRows.filter(x => x.some(v => v)).map(toLine).join('\r\n') + '\r\n', ['--backup', backup]);
  check('backup: renamed course used by a teaching class → ERROR',
    r.code === 1 && /teachingAssignments\/zzLec\/zzRef: course .* would no longer exist/.test(r.out));
}

check('the real seed file was not changed', fs.readFileSync(SEED, 'utf8') === seedTextBefore);
fs.rmSync(TMP, { recursive: true, force: true });
console.log(`----\n${pass} passed, ${fail} failed`);
process.exitCode = fail ? 1 : 0;
