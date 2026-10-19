#!/usr/bin/env node
/*
 * Builds the studentIds/{studentId} -> uid index for students who signed up before the index
 * existed, so a new account can't take their ID. Reads an export of /students, writes the index,
 * and lists any student ID already used by more than one account (those are left out: sort them
 * out by hand first, then run this again).
 *
 *   firebase database:get /students > students.json
 *   node tools/build_student_ids.js students.json student_ids.json
 *   firebase database:update /studentIds student_ids.json
 */
'use strict';

const fs = require('fs');

/** { studentIds, duplicates } from a /students export. duplicates maps an ID to its uids. */
function buildStudentIds(students) {
  const owners = {};
  for (const [uid, profile] of Object.entries(students || {})) {
    const id = profile && profile.studentId != null ? String(profile.studentId) : '';
    if (!/^[0-9]{10}$/.test(id)) continue;
    (owners[id] = owners[id] || []).push(uid);
  }
  const studentIds = {};
  const duplicates = {};
  for (const [id, uids] of Object.entries(owners)) {
    if (uids.length === 1) studentIds[id] = uids[0];
    else duplicates[id] = uids.sort();
  }
  return { studentIds, duplicates };
}

if (require.main === module) {
  const [input, output] = process.argv.slice(2);
  if (!input || !output) {
    console.error('Usage: node tools/build_student_ids.js <students.json> <student_ids.json>');
    process.exit(1);
  }
  const { studentIds, duplicates } = buildStudentIds(JSON.parse(fs.readFileSync(input, 'utf8')));
  fs.writeFileSync(output, JSON.stringify(studentIds, null, 2) + '\n');
  console.log(`wrote ${Object.keys(studentIds).length} student IDs to ${output}`);
  const dupIds = Object.keys(duplicates);
  if (dupIds.length) {
    console.log(`\n${dupIds.length} student ID(s) used by more than one account (left out):`);
    for (const id of dupIds) console.log(`  ${id}: ${duplicates[id].join(', ')}`);
    process.exitCode = 2;
  }
}

module.exports = { buildStudentIds };
