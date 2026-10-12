#!/usr/bin/env bash
# Security-rules tests for database.rules.json, run ONLY against the
# Firebase Local Emulator Suite (Realtime Database emulator on port 9000).
#
# It never talks to the live attendra-uitm database: it uses no real account,
# no API key and no token. The emulator is wiped and re-seeded at the start
# of every run, from seed/attendra_seed.json plus fake zzTest_ users.
#
# Usage (Git Bash, from the project folder):
#   1. Terminal 1:  firebase emulators:start --only database
#   2. Terminal 2:  bash tools/rules_test.sh
# Every case prints PASS/FAIL against what database.rules.json should allow.

# ---------------------------------------------------------------- safety guard
HOST="${FIREBASE_DATABASE_EMULATOR_HOST:-127.0.0.1:9000}"
DB="${RULES_TEST_DB_URL:-http://$HOST}"
case "$DB" in
  *firebasedatabase.app*|*firebaseio.com*|https://*)
    echo "ERROR: rules_test.sh only runs against the local emulator, not $DB" >&2
    echo "Live database tests must be READ-ONLY: use tools/rules_smoke_live.sh" >&2
    exit 1 ;;
  http://127.0.0.1:*|http://localhost:*) ;;
  *)
    echo "ERROR: $DB is not the local emulator (expected http://127.0.0.1:9000)" >&2
    exit 1 ;;
esac
NS=attendra-uitm-default-rtdb
P="${1:-.}"
SEED="$P/seed/attendra_seed.json"
[ -f "$SEED" ] || { echo "ERROR: $SEED not found (run from the project folder)" >&2; exit 1; }
curl -s -o /dev/null --max-time 3 "$DB/.json?ns=$NS&shallow=true" \
  || { echo "ERROR: emulator not running at $DB. Start it with: firebase emulators:start --only database" >&2; exit 1; }

# -------------------------------------------------------------------- helpers
# The emulator accepts unsigned tokens ("alg":"none"), so fake users need no
# real sign-in. The payload only has to name the uid.
b64url() { base64 | tr -d '=\n' | tr '/+' '_-'; }
token() {
  local now; now=$(date +%s)
  local h p
  h=$(printf '{"alg":"none","typ":"JWT"}' | b64url)
  p=$(printf '{"sub":"%s","user_id":"%s","iat":%s,"exp":%s,"auth_time":%s,"aud":"attendra-uitm","iss":"https://securetoken.google.com/attendra-uitm"}' \
        "$1" "$1" "$now" $((now + 3600)) "$now" | b64url)
  echo "$h.$p."
}
# owner <method> <path> <body>: admin write that skips the rules (seeding only).
owner() {
  curl -s -o /dev/null -w '%{http_code}' -X "$1" "$DB/$2.json?ns=$NS" \
    -H 'Authorization: Bearer owner' --data-binary "$3"
}

pass=0; fail=0
# check <name> <expect allow|deny> <method> <path> [token] [body]
check() {
  local name=$1 expect=$2 method=$3 path=$4 tok=$5 body=$6 url code
  url="$DB/$path.json?ns=$NS"; [ -n "$tok" ] && url="$url&auth=$tok"
  if [ -n "$body" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" --data-binary "$body")
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url")
  fi
  local got=deny; [ "$code" = 200 ] && got=allow
  if [ "$got" = "$expect" ]; then pass=$((pass+1)); echo "PASS  $name ($code)";
  else fail=$((fail+1)); echo "FAIL  $name: expected $expect, got $got ($code)"; fi
}

# --------------------------------------------------------------- test fixture
# Every key the tests create starts with zzTest_ so it is easy to spot.
SU=zzTest_stu; SU2=zzTest_stu2; LU=zzTest_lec; LU2=zzTest_lec2
ST=$(token $SU); ST2=$(token $SU2); LT=$(token $LU); LT2=$(token $LU2)
SID=zzTest_s1
NOW=$(( $(date +%s) * 1000 ))
START=$((NOW - 600000)); LATE=$((NOW + 1800000)); END=$((NOW + 3600000))
LAT=3.0695; LNG=101.5   # fake CS1 position, emulator only

echo "Seeding the emulator at $DB ..."
[ "$(owner PUT '' "@$SEED")" = 200 ] || { echo "ERROR: could not load $SEED into the emulator" >&2; exit 1; }
FIXTURE=$(cat <<EOF
{
  "buildings/CS1/lat": $LAT, "buildings/CS1/lng": $LNG,
  "roles/$SU": "student", "roles/$SU2": "student", "roles/$LU": "lecturer", "roles/$LU2": "lecturer",
  "students/$SU":  {"name":"zzTest Student","email":"zztest.stu@student.uitm.edu.my","phone":"0","studentId":"2025009901",
                    "programme":"CS240","part":5,"groupId":"CDCS2405A","emailVerified":true,"isTest":true,"createdAt":1},
  "students/$SU2": {"name":"zzTest Student Two","email":"zztest.stu2@student.uitm.edu.my","phone":"0","studentId":"2025009902",
                    "programme":"CS240","part":5,"groupId":"CDCS2405B","emailVerified":true,"createdAt":1},
  "lecturers/$LU":  {"name":"zzTest Lecturer","email":"zztest.lec@uitm.edu.my","phone":"0","staffId":"zzTest1","emailVerified":true,"createdAt":1},
  "lecturers/$LU2": {"name":"zzTest Lecturer Two","email":"zztest.lec2@uitm.edu.my","phone":"0","staffId":"zzTest2","emailVerified":true,"createdAt":1},
  "studentsByGroup/CDCS2405A/$SU": true, "studentsByGroup/CDCS2405B/$SU2": true,
  "studentClubs/$SU/ITEC": true, "clubMembers/ITEC/$SU": true, "clubAdvisors/ITEC/$LU": true,
  "teachingAssignments/$LU/CS240_ICT600_CDCS2405A": {"courseId":"CS240_ICT600","groupId":"CDCS2405A"},
  "sessions/$SID": {"lecturerUid":"$LU","type":"class","title":"zzTest class","courseId":"CS240_ICT600",
                    "groups":{"CDCS2405A":true},"where":"building","building":"CS1","lat":$LAT,"lng":$LNG,"radius":60,
                    "start":$START,"end":$END,"lateAfter":$LATE,"status":"open","createdAt":$NOW},
  "sessionsByLecturer/$LU/$SID": true, "sessionsByGroup/CDCS2405A/$SID": true
}
EOF
)
[ "$(owner PATCH '' "$FIXTURE")" = 200 ] || { echo "ERROR: could not write the zzTest_ fixture" >&2; exit 1; }
echo "student=$SU lecturer=$LU session=$SID"
echo "----"

TS='{".sv":"timestamp"}'
# A new Class session (plus its indexes) for <group>, as one multi-path update.
new_session() {
  echo '{"sessions/'$1'":{"lecturerUid":"'$LU'","type":"class","title":"zzTest new","courseId":"CS240_ICT600",
    "groups":{"'$2'":true},"where":"building","building":"CS1","lat":'$LAT',"lng":'$LNG',"radius":60,
    "start":'$START',"end":'$END',"lateAfter":'$LATE',"status":"open","createdAt":'$TS'},
    "sessionsByLecturer/'$LU'/'$1'":true,"sessionsByGroup/'$2'/'$1'":true}'
}

# Seed data
check "signed out reads programmes"        allow GET programmes
check "signed out reads courses"           deny  GET courses
check "student reads buildings"            allow GET buildings "$ST"
check "student writes buildings"           deny  PATCH buildings "$ST" '{"zzTest_CS9":{"name":"x"}}'

# Accounts
check "student reads own role"             allow GET "roles/$SU" "$ST"
check "student changes own role"           deny  PUT "roles/$SU" "$ST" '"lecturer"'
check "student reads own profile"          allow GET "students/$SU" "$ST"
check "student reads lecturer profile"     deny  GET "lecturers/$LU" "$ST"
check "lecturer reads student profile"     allow GET "students/$SU" "$LT"
check "student sets own isTest"            deny  PUT "students/$SU/isTest" "$ST" 'false'
check "student changes own groupId"        deny  PUT "students/$SU/groupId" "$ST" '"CDCS2405B"'
check "student sets emailVerified (no code)" deny PUT "students/$SU/emailVerified" "$ST" 'true'
check "student edits own phone"            allow PUT "students/$SU/phone" "$ST" '"0123456789"'
check "student reads all students"         deny  GET students "$ST"

# Teaching assignments and clubs
check "lecturer reads own assignments"     allow GET "teachingAssignments/$LU" "$LT"
check "student reads teachingAssignments"  deny  GET "teachingAssignments/$LU" "$ST"

# Teaching classes added by the lecturer (C5b). <course> <group> [extra JSON]
assignment() { echo '{"courseId":"'$1'","groupId":"'$2'","semester":"September 2026 – February 2027","createdAt":'"$TS$3"'}'; }
check "lecturer adds own class"            allow PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2405B" "$LT" "$(assignment CS240_ICT602 CDCS2405B)"
check "lecturer writes another lecturer's node" deny PUT "teachingAssignments/$LU2/CS240_ICT602_CDCS2405B" "$LT" "$(assignment CS240_ICT602 CDCS2405B)"
check "student writes teachingAssignments" deny  PUT "teachingAssignments/$SU/CS240_ICT602_CDCS2405B" "$ST" "$(assignment CS240_ICT602 CDCS2405B)"
check "key not equal to courseId_groupId"  deny  PUT "teachingAssignments/$LU/zzTest_wrongkey" "$LT" "$(assignment CS240_ICT602 CDCS2405C)"
check "course from another programme"      deny  PUT "teachingAssignments/$LU/CS230_P5_INTRODUCTION_TO_TECHNOPRENEURSHIP_CDCS2405C" "$LT" \
  "$(assignment CS230_P5_INTRODUCTION_TO_TECHNOPRENEURSHIP CDCS2405C)"
check "course from another part"           deny  PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2404A" "$LT" "$(assignment CS240_ICT602 CDCS2404A)"
check "unknown course"                     deny  PUT "teachingAssignments/$LU/zzTest_NOPE_CDCS2405C" "$LT" "$(assignment zzTest_NOPE CDCS2405C)"
check "unknown group"                      deny  PUT "teachingAssignments/$LU/CS240_ICT602_zzTest_NOPE" "$LT" "$(assignment CS240_ICT602 zzTest_NOPE)"
check "extra field"                        deny  PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2405C" "$LT" \
  "$(assignment CS240_ICT602 CDCS2405C ',"room":"zzTest"')"
check "lecturer removes own class"         allow DELETE "teachingAssignments/$LU/CS240_ICT602_CDCS2405B" "$LT"
check "lecturer reads clubAdvisors"        allow GET clubAdvisors "$LT"
check "student reads clubAdvisors"         deny  GET clubAdvisors "$ST"
check "student reads studentsByGroup"      deny  GET "studentsByGroup/CDCS2405A" "$ST"
check "lecturer reads studentsByGroup"     allow GET "studentsByGroup/CDCS2405A" "$LT"

# Sessions
check "student reads a session"            allow GET "sessions/$SID" "$ST"
check "student creates a session"          deny  PUT "sessions/zzTest_s9" "$ST" '{"lecturerUid":"'$SU'"}'
check "lecturer creates session, assigned group"   allow PATCH "" "$LT" "$(new_session zzTest_s2 CDCS2405A)"
check "lecturer creates session, unassigned group" deny  PATCH "" "$LT" "$(new_session zzTest_s3 CDCS2405B)"
check "other lecturer edits the session"   deny  PATCH "sessions/$SID" "$LT2" '{"title":"zzTest hijack"}'
check "lecturer deletes own session"       deny  DELETE "sessions/$SID" "$LT"

# Check-in and attendance
check "student checks in as late before lateAfter" deny PATCH "" "$ST" \
  '{"attendance/'$SID'/'$SU'":{"status":"late","time":'$TS',"method":"gps","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5},
    "studentAttendance/'$SU'/'$SID'":{"status":"late","time":'$TS'}}'
check "student from another group checks in" deny PATCH "" "$ST2" \
  '{"attendance/'$SID'/'$SU2'":{"status":"present","time":'$TS',"method":"gps","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5},
    "studentAttendance/'$SU2'/'$SID'":{"status":"present","time":'$TS'}}'
check "student checks in (gps, present)"   allow PATCH "" "$ST" \
  '{"attendance/'$SID'/'$SU'":{"status":"present","time":'$TS',"method":"gps","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5},
    "studentAttendance/'$SU'/'$SID'":{"status":"present","time":'$TS'},
    "attempts/'$SID'/zzTest_a1":{"uid":"'$SU'","time":'$TS',"result":"accepted","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5}}'
check "lecturer reads own session attendance" allow GET "attendance/$SID" "$LT"
check "student reads whole attendance"     deny  GET "attendance/$SID" "$ST"
check "student reads own record"           allow GET "attendance/$SID/$SU" "$ST"
check "student overwrites own record"      deny  PATCH "attendance/$SID/$SU" "$ST" '{"status":"late"}'
check "student logs a rejected attempt"    allow PUT "attempts/$SID/zzTest_a2" "$ST" \
  '{"uid":"'$SU'","time":'$TS',"result":"rejected","reason":"OUTSIDE_RADIUS","distance":400,"accuracy":8,"lat":3.08,"lng":101.5}'
check "student edits a logged attempt"     deny  PUT "attempts/$SID/zzTest_a2/result" "$ST" '"accepted"'
check "student fakes accepted attempt"     deny  PUT "attempts/$SID/zzTest_a3" "$ST2" '{"uid":"'$SU2'","time":'$TS',"result":"accepted","lat":3.07,"lng":101.5}'
check "student reads attempts"             deny  GET "attempts/$SID" "$ST"
check "student reads own notifications"    allow GET "notifications/$SU" "$ST"
check "lecturer reads student notifications" deny GET "notifications/$SU" "$LT"
check "student reads own mirror"           allow GET "studentAttendance/$SU" "$ST"
# studentAttendance must carry a status that matches an attendance record written
# in the same update (zzTest_none is a session id that does not exist).
check "mirror with no status"              deny  PUT "studentAttendance/$SU/zzTest_none" "$ST" '{"time":'$TS'}'
check "mirror without a matching record"   deny  PUT "studentAttendance/$SU/zzTest_none" "$ST" '{"status":"present","time":'$TS'}'
check "mirror for someone else"            deny  PUT "studentAttendance/$LU/zzTest_none" "$ST" '{"status":"present","time":'$TS'}'

echo "----"; echo "$pass passed, $fail failed"
[ "$fail" = 0 ]
