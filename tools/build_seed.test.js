#!/usr/bin/env node
/* Tests for tools/build_seed.js. Runs the builder on copies of the real CSVs, never writes to seed/. */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { parseCsv } = require('./build_seed');

const ROOT = path.resolve(__dirname, '..');
const SEED_DIR = path.join(ROOT, 'seed');
const SEED = path.join(SEED_DIR, 'attendra_seed.json');
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'attendra-seed-test-'));
const seedTextBefore = fs.readFileSync(SEED, 'utf8');
const seed = JSON.parse(seedTextBefore);
const real = {
  structure: fs.readFileSync(path.join(SEED_DIR, 'programme_structure.csv'), 'utf8'),
  options: fs.readFileSync(path.join(SEED_DIR, 'elective_options.csv'), 'utf8'),
  groups: fs.readFileSync(path.join(SEED_DIR, 'groups.csv'), 'utf8'),
};

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass++; console.log(`PASS  ${name}`); }
  else { fail++; console.log(`FAIL  ${name}${detail ? ': ' + detail : ''}`); }
}

/** Runs the builder on (possibly tweaked) CSV texts; returns {code, out, dir}. */
let n = 0;
function run(csv = {}, extra = []) {
  const dir = path.join(TMP, `case${++n}`);
  fs.mkdirSync(dir);
  const files = [];
  for (const k of ['structure', 'options', 'groups']) {
    const f = path.join(dir, `${k}.csv`);
    fs.writeFileSync(f, csv[k] !== undefined ? csv[k] : real[k]);
    files.push(`--${k}`, f);
  }
  try {
    const out = execFileSync(process.execPath,
      [path.join(__dirname, 'build_seed.js'), ...files, '--seed', SEED, '--out', dir, ...extra],
      { encoding: 'utf8' });
    return { code: 0, out, dir };
  } catch (e) {
    return { code: e.status, out: e.stdout, dir };
  }
}
const read = (dir, f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
const written = dir => fs.existsSync(path.join(dir, 'import_courses.json'));
// Swap one exact line in a CSV text; fails loudly if the line isn't there so a test can't pass by accident.
function swap(text, from, to) {
  if (!text.includes(from)) throw new Error(`test setup: line not found: ${from}`);
  return text.replace(from, to);
}

// ---------------------------------------------------------------- the CSV reader
const parsed = parseCsv('﻿a,b\r\n"x, y","say ""hi"""\r\n');
check('CSV: BOM, CRLF, quoted comma and "" quotes', parsed[1][0] === 'x, y' && parsed[1][1] === 'say "hi"' && parsed[0][0] === 'a');

// ---------------------------------------------------------------- the real CSVs
let r = run();
check('real CSVs, strict: no errors and files written', r.code === 0 && written(r.dir), r.out.split('\n').filter(l => l.startsWith('ERROR')).join(' | '));
const courses = read(r.dir, 'import_courses.json');
const groups = read(r.dir, 'import_groups.json');
const programmes = read(r.dir, 'import_programmes.json');
const whole = read(r.dir, 'attendra_seed.built.json');

check('no course is made from an ELECTIVE slot', !Object.keys(courses).some(id => id.endsWith('_ELECTIVE')));
check('every course ID is {programme}_{code}', Object.entries(courses).every(([id, c]) => id === `${c.programme}_${c.code}`));
const acc = courses.CS259_ACC407;
check('CS259 ACC407: parts 2, 3 and 5, part = 2',
  acc && JSON.stringify(acc.parts) === JSON.stringify({ 2: true, 3: true, 5: true }) && acc.part === 2);
const csc574 = courses.CS230_CSC574;
check('CS230 CSC574: Part 4 elective + Part 5 in both tracks',
  csc574 && csc574.parts['4'] && csc574.parts['5'] && csc574.slotType === 'ELECTIVE'
  && JSON.stringify(csc574.tracks) === JSON.stringify({ SOFT_COMPUTING: 5, BIG_DATA: 5 }));
check('CS240 ICT606: the part depends on the track',
  JSON.stringify(courses.CS240_ICT606.tracks) === JSON.stringify({ CREATIVE_IT: 5, IT_INFRASTRUCTURE: 6, BIG_DATA: 5 }));
check('every tracks part is also in the parts map', Object.values(courses).every(c =>
  Object.values(c.tracks || {}).every(p => c.parts[String(p)])));
check('no tracks node on CORE / UNIVERSITY / PROGRAMME_ELECTIVE courses (CS240 ICT550, CS259 ACC407)',
  !('tracks' in courses.CS240_ICT550) && !('tracks' in courses.CS259_ACC407));
check('fixed elective keeps slotType ELECTIVE and no tracks (CS259 DSC551)',
  courses.CS259_DSC551 && courses.CS259_DSC551.slotType === 'ELECTIVE' && !courses.CS259_DSC551.tracks);
check('blank credit hours are left out (CS255 PHI428)', courses.CS255_PHI428 && !('creditHours' in courses.CS255_PHI428));
check('every programme: sulamCourse ICT652, sulamPart 6',
  Object.values(programmes).every(p => p.sulamCourse === 'ICT652' && p.sulamPart === 6));
check('exactly one isSulam course per programme',
  Object.keys(programmes).every(p => Object.values(courses).filter(c => c.programme === p && c.isSulam).length === 1));
check('CS259 has its two track labels', programmes.CS259.tracks.BIG_DATA === 'Big Data'
  && programmes.CS259.tracks.INTELLIGENT_SYSTEMS === 'Intelligent Systems');
check('trackStartPart: CS259 = 4, CS230 / CS240 = 5, none for CS255',
  programmes.CS259.trackStartPart === 4 && programmes.CS230.trackStartPart === 5
  && programmes.CS240.trackStartPart === 5 && !('trackStartPart' in programmes.CS255));
check('groups: A-C for every programme and part', Object.entries(seed.programmes).every(([p, x]) => {
  for (let part = 1; part <= x.parts; part++) for (const l of 'ABC') if (!groups[`CD${p}${part}${l}`]) return false;
  return true;
}));
check('groups: extra letters from groups.csv (CDCS2594F)', groups.CDCS2594F && groups.CDCS2594F.letter === 'F');
check('groups: track saved (CDCS2306C = Soft Computing)', groups.CDCS2306C.track === 'SOFT_COMPUTING');
check('groups: no track where groups.csv has none (CDCS2593A)', !('track' in groups.CDCS2593A));
check('buildings, venues and clubs copied unchanged',
  ['buildings', 'venues', 'clubs'].every(k => JSON.stringify(whole[k]) === JSON.stringify(seed[k])));

// ---------------------------------------------------------------- things that must stop the build
const ict600 = 'CS240,5,CORE,ICT600,Web Technology and Application,3,,FALSE,VERIFIED,';
r = run({ structure: swap(real.structure, ict600, ict600.replace('FALSE', 'TRUE')) });
check('two SULAM courses in one programme: ERROR', r.code === 1 && /CS240: 2 courses are marked isSulam/.test(r.out) && !written(r.dir));

r = run({ structure: swap(real.structure, ict600, ict600.replace('ICT600', 'TBC')) });
check('TBC code: ERROR', r.code === 1 && /still TBC/.test(r.out));
r = run({ structure: swap(real.structure, ict600, ict600.replace('VERIFIED', 'TBC')) });
check('codeStatus TBC: ERROR', r.code === 1 && /still TBC/.test(r.out));

r = run({ structure: swap(real.structure, ict600, ict600.replace('Web Technology and Application', 'Web Tech')) + 'CS255,6,CORE,ICT600,Web Technology and Application,3,,FALSE,VERIFIED,\r\n' });
check('same code, different name: ERROR', r.code === 1 && /ICT600 is called/.test(r.out));

r = run({ structure: real.structure + 'CS240,5,CORE,ICT600,Web Technology and Application,3,,FALSE,VERIFIED,\r\n' });
check('same course twice in one part: ERROR', r.code === 1 && /already in CS240 Part 5/.test(r.out));

// CS240 Part 5 has 3 track slots; drop one Creative IT Part 5 course so that track runs short.
r = run({ options: swap(real.options, 'CS240,TRACK:CREATIVE_IT,Creative IT,ICT603,Information Architecture,3,5,VERIFIED,\r\n', '') });
check('track pool too small for its slots: ERROR', r.code === 1 && /CREATIVE_IT only has 2 course/.test(r.out));

r = run({ structure: real.structure + 'CS240,3,ELECTIVE,ELECTIVE,Elective X,3,TRACK,FALSE,VERIFIED,\r\n' });
check('track slot in Part 3: ERROR', r.code === 1 && /tracks start in Part 5 for CS240/.test(r.out));
r = run({ options: real.options + 'CS230,TRACK:BIG_DATA,Big Data,ZZZ401,Made Up,3,4,VERIFIED,\r\n' });
check('track pool course in Part 4 (not CS259): ERROR', r.code === 1 && /tracks start in Part 5 for CS230/.test(r.out));
r = run({ groups: real.groups + 'CS230,4,D,CDCS2304D,TRACK:BIG_DATA,\r\n' });
check('group with a track in Part 4 (not CS259): ERROR', r.code === 1 && /tracks start in Part 5 for CS230/.test(r.out));
r = run({ groups: real.groups + 'CS230,5,D,CDCS2305D,TRACK:NOPE,\r\n' });
check('group with an unknown track: ERROR', r.code === 1 && /TRACK:NOPE is not one of CS230's tracks/.test(r.out));

r = run({ structure: real.structure + 'CS255,5,ELECTIVE,ELECTIVE,Elective X,3,TRACK,FALSE,VERIFIED,\r\n' });
check('TRACK slot in a programme with no tracks: ERROR', r.code === 1 && /CS255 has no tracks/.test(r.out));
r = run({ structure: real.structure + 'CS255,5,ELECTIVE,ELECTIVE,Elective X,3,,FALSE,VERIFIED,\r\n' });
check('ELECTIVE slot with no optionGroup: ERROR', r.code === 1 && /needs an optionGroup/.test(r.out));

r = run({ structure: swap(real.structure, ict600, ict600.replace('CS240,5', 'CS240,9')) });
check('part outside 1-6: ERROR', r.code === 1 && /part 9 is outside 1-6/.test(r.out));
r = run({ structure: swap(real.structure, ict600, ict600.replace('CS240', 'CS999')) });
check('unknown programme: ERROR', r.code === 1 && /programme "CS999" is not in seed programmes/.test(r.out));
r = run({ structure: 'programme,part,courseCode\r\nCS240,1,CSC415\r\n' });
check('missing column: clear error', r.code === 1);

r = run({ structure: swap(real.structure, ict600, ict600.replace('VERIFIED', 'TBC')) }, ['--draft']);
check('--draft writes even with errors', r.code === 0 && written(r.dir) && /DRAFT/.test(r.out));

// ---------------------------------------------------------------- --backup check
const backup = path.join(TMP, 'backup.json');
fs.writeFileSync(backup, JSON.stringify({
  teachingAssignments: { zzLec: {
    ok: { courseId: 'CS259_ACC407', groupId: 'CDCS2593C' },
    gone: { courseId: 'CS255_P6_PROJECT', groupId: 'CDCS2556A' },
  } },
  sessions: { zzS1: { courseId: 'CS240_ICT652', groups: { CDCS2406A: true } } },
  students: { zzStu: { groupId: 'CDCS2594F' } },
}));
r = run({}, ['--backup', backup]);
check('backup: old course ID still used by a teaching class → ERROR',
  r.code === 1 && /teachingAssignments\/zzLec\/gone: course CS255_P6_PROJECT would no longer exist/.test(r.out)
  && !/zzLec\/ok/.test(r.out) && !/zzS1|zzStu/.test(r.out));

check('the real seed file was not changed', fs.readFileSync(SEED, 'utf8') === seedTextBefore);
fs.rmSync(TMP, { recursive: true, force: true });
console.log(`----\n${pass} passed, ${fail} failed`);
process.exitCode = fail ? 1 : 0;
