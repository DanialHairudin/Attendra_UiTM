#!/usr/bin/env bash
# Security-rules tests for database.rules.json, run ONLY against the Firebase Local Emulator Suite
# (Realtime Database emulator on port 9000).

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
# The seed tools/build_seed.js makes from the CSVs, since that's what the live database will have.
SEED="$P/seed/attendra_seed.built.json"
[ -f "$SEED" ] || { echo "ERROR: $SEED not found. Run node tools/build_seed.js first (from the project folder)" >&2; exit 1; }
curl -s -o /dev/null --max-time 3 "$DB/.json?ns=$NS&shallow=true" \
  || { echo "ERROR: emulator not running at $DB. Start it with: firebase emulators:start --only database" >&2; exit 1; }

# -------------------------------------------------------------------- helpers
# The emulator accepts unsigned tokens ("alg":"none"), so fake users need no real sign-in.
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
# Extra query string for the next check only (e.g. orderBy/equalTo), then cleared.
QS=""
# check <name> <expect allow|deny> <method> <path> [token] [body]
check() {
  local name=$1 expect=$2 method=$3 path=$4 tok=$5 body=$6 url code
  url="$DB/$path.json?ns=$NS$QS"; [ -n "$tok" ] && url="$url&auth=$tok"; QS=""
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
# Tester accounts (isTest: true): kept apart from the real ones (data/TestWorld).
TSU=zzTest_tstu; TLU=zzTest_tlec; TST=$(token $TSU); TLT=$(token $TLU)
TSID=zzTest_ts1
SID=zzTest_s1
# Closed sessions for the delete / hide tests: CSID and CSID3 are LU's, CSID2 is LU2's.
CSID=zzTest_c1; CSID2=zzTest_c2; CSID3=zzTest_c3
NOW=$(( $(date +%s) * 1000 ))
START=$((NOW - 600000)); LATE=$((NOW + 1800000)); END=$((NOW + 3600000))
LAT=3.0695; LNG=101.5   # Fake CS1 position, emulator only

echo "Seeding the emulator at $DB ..."
[ "$(owner PUT '' "@$SEED")" = 200 ] || { echo "ERROR: could not load $SEED into the emulator" >&2; exit 1; }
FIXTURE=$(cat <<EOF
{
  "buildings/CS1/lat": $LAT, "buildings/CS1/lng": $LNG,
  "roles/$TSU": "student", "roles/$TLU": "lecturer", "roles/$SU": "student", "roles/$SU2": "student", "roles/$LU": "lecturer", "roles/$LU2": "lecturer",
  "students/$SU":  {"name":"zzTest Student","email":"zztest.stu@student.uitm.edu.my","phone":"0","studentId":"2025009901",
                    "programme":"CS240","part":5,"groupId":"CDCS2405A","emailVerified":true,"createdAt":1},
  "students/$TSU": {"name":"zzTest Tester","email":"zztest.tstu@student.uitm.edu.my","phone":"0","studentId":"2025009903",
                    "programme":"CS240","part":5,"groupId":"CDCS2405A","emailVerified":true,"isTest":true,"createdAt":1},
  "lecturers/$TLU": {"name":"zzTest Test Lecturer","email":"zztest.tlec@uitm.edu.my","phone":"0","staffId":"zzTest3","emailVerified":true,"isTest":true,"createdAt":1},
  "studentsByGroup/CDCS2405A/$TSU": true,
  "teachingAssignments/$TLU/CS240_ICT600_CDCS2405A": {"courseId":"CS240_ICT600","groupId":"CDCS2405A"},
  "sessions/$TSID": {"lecturerUid":"$TLU","type":"class","title":"zzTest test class","courseId":"CS240_ICT600",
                    "groups":{"CDCS2405A":true},"where":"building","building":"CS1","lat":$LAT,"lng":$LNG,"radius":60,
                    "start":$START,"end":$END,"lateAfter":$LATE,"status":"open","createdAt":$NOW,"isTest":true},
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
  "sessionsByLecturer/$LU/$SID": true, "sessionsByGroup/CDCS2405A/$SID": true,
  "sessions/$CSID": {"lecturerUid":"$LU","type":"class","title":"zzTest closed","courseId":"CS240_ICT600",
                    "groups":{"CDCS2405A":true},"where":"building","building":"CS1","lat":$LAT,"lng":$LNG,"radius":60,
                    "start":$START,"end":$END,"lateAfter":$LATE,"status":"closed","createdAt":$NOW},
  "sessionsByLecturer/$LU/$CSID": true, "sessionsByGroup/CDCS2405A/$CSID": true,
  "attendance/$CSID/$SU": {"status":"present","time":$NOW,"method":"gps","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5},
  "studentAttendance/$SU/$CSID": {"status":"present","time":$NOW},
  "attempts/$CSID/zzTest_ca1": {"uid":"$SU","time":$NOW,"result":"accepted","lat":3.0695,"lng":101.5},
  "notifications/$SU/zzTest_n1": {"title":"zzTest","body":"zzTest","sessionId":"$CSID","time":$NOW,"read":false},
  "notifications/$SU/zzTest_n2": {"title":"zzTest","body":"zzTest","sessionId":"$SID","time":$NOW,"read":false},
  "notifications/$SU2/zzTest_n3": {"title":"zzTest","body":"zzTest","sessionId":"$SID","time":$NOW,"read":false},
  "sessions/$CSID2": {"lecturerUid":"$LU2","type":"class","title":"zzTest other closed","courseId":"CS240_ICT600",
                    "groups":{"CDCS2405A":true},"where":"building","building":"CS1","lat":$LAT,"lng":$LNG,"radius":60,
                    "start":$START,"end":$END,"lateAfter":$LATE,"status":"closed","createdAt":$NOW},
  "sessionsByLecturer/$LU2/$CSID2": true, "sessionsByGroup/CDCS2405A/$CSID2": true,
  "sessions/$CSID3": {"lecturerUid":"$LU","type":"class","title":"zzTest closed to hide","courseId":"CS240_ICT600",
                    "groups":{"CDCS2405A":true},"where":"building","building":"CS1","lat":$LAT,"lng":$LNG,"radius":60,
                    "start":$START,"end":$END,"lateAfter":$LATE,"status":"closed","createdAt":$NOW},
  "attendance/$CSID3/$SU": {"status":"absent","time":$NOW,"method":"auto"},
  "studentAttendance/$SU/$CSID3": {"status":"absent","time":$NOW}
}
EOF
)
[ "$(owner PATCH '' "$FIXTURE")" = 200 ] || { echo "ERROR: could not write the zzTest_ fixture" >&2; exit 1; }
echo "student=$SU lecturer=$LU session=$SID"
echo "----"

TS='{".sv":"timestamp"}'
# A new Class session (plus its indexes) for <group>, as one multi-path update.
new_session() {
  local lu=${3:-$LU}
  echo '{"sessions/'$1'":{"lecturerUid":"'$lu'","type":"class","title":"zzTest new","courseId":"CS240_ICT600",
    "groups":{"'$2'":true},"where":"building","building":"CS1","lat":'$LAT',"lng":'$LNG',"radius":60,
    "start":'$START',"end":'$END',"lateAfter":'$LATE',"status":"open","createdAt":'$TS$4'},
    "sessionsByLecturer/'$lu'/'$1'":true,"sessionsByGroup/'$2'/'$1'":true}'
}
# Check-in update for <student uid> <session id>
checkin() {
  echo '{"attendance/'$2'/'$1'":{"status":"present","time":'$TS',"method":"gps","distance":10,"accuracy":8,"lat":3.0695,"lng":101.5},
    "studentAttendance/'$1'/'$2'":{"status":"present","time":'$TS'}}'
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
check "student sets own isTest"            deny  PUT "students/$SU/isTest" "$ST" 'true'
check "tester clears own isTest"           deny  PUT "students/$TSU/isTest" "$TST" 'false'
check "student changes own groupId"        deny  PUT "students/$SU/groupId" "$ST" '"CDCS2405B"'
check "student sets emailVerified (no code)" deny PUT "students/$SU/emailVerified" "$ST" 'true'
check "student edits own phone"            allow PUT "students/$SU/phone" "$ST" '"0123456789"'

# My track. $SU is CS240 Part 5 in CDCS2405A, which has no group track, so they pick their own.
# Two extra students: one in a group that has a track, one in a part before tracks start.
TRU=zzTest_trk1; TRU2=zzTest_trk2; TRT=$(token $TRU); TRT2=$(token $TRU2)
owner PATCH '' '{"roles/'$TRU'":"student","roles/'$TRU2'":"student",
  "students/'$TRU'":{"name":"zzTest Track","email":"zztest.trk1@student.uitm.edu.my","studentId":"2025009911",
                     "programme":"CS230","part":6,"groupId":"CDCS2306A","emailVerified":true,"createdAt":1},
  "students/'$TRU2'":{"name":"zzTest Track Two","email":"zztest.trk2@student.uitm.edu.my","studentId":"2025009912",
                     "programme":"CS240","part":4,"groupId":"CDCS2404A","emailVerified":true,"createdAt":1}}' > /dev/null
check "student picks own track"            allow PUT "students/$SU/track" "$ST" '"CREATIVE_IT"'
check "student picks unknown track"        deny  PUT "students/$SU/track" "$ST" '"zzTest_NOPE"'
check "student picks another programme's track" deny PUT "students/$SU/track" "$ST" '"SOFT_COMPUTING"'
check "student sets another student's track" deny PUT "students/$SU2/track" "$ST" '"CREATIVE_IT"'
check "track when the group already has one" deny PUT "students/$TRU/track" "$TRT" '"SOFT_COMPUTING"'
check "track before tracks start (CS240 Part 4)" deny PUT "students/$TRU2/track" "$TRT2" '"CREATIVE_IT"'

# Picking a track at sign-up: role + profile in one write, the same way the app does it.
# signup <uid> <programme> <part> <group> [track]
signup() {
  local track=""; [ -n "$5" ] && track=',"track":"'$5'"'
  echo '{"roles/'$1'":"student","students/'$1'":{"name":"zzTest Signup","email":"'$1'@student.uitm.edu.my",
    "studentId":"20250099'$((RANDOM % 90 + 10))'","programme":"'$2'","part":'$3',"groupId":"'$4'",
    "emailVerified":false,"createdAt":{".sv":"timestamp"}'$track'}}'
}
check "sign-up, picks a track (CS240 Part 5)"   allow PATCH "" "$(token zzTest_su1)" "$(signup zzTest_su1 CS240 5 CDCS2405C CREATIVE_IT)"
check "sign-up, no track (still allowed)"       allow PATCH "" "$(token zzTest_su2)" "$(signup zzTest_su2 CS240 5 CDCS2405C)"
check "sign-up, track but group has one"        deny  PATCH "" "$(token zzTest_su3)" "$(signup zzTest_su3 CS230 6 CDCS2306A SOFT_COMPUTING)"
check "sign-up, track before tracks start"      deny  PATCH "" "$(token zzTest_su4)" "$(signup zzTest_su4 CS240 4 CDCS2404A CREATIVE_IT)"
check "sign-up, another programme's track"      deny  PATCH "" "$(token zzTest_su5)" "$(signup zzTest_su5 CS240 5 CDCS2405C SOFT_COMPUTING)"
check "student reads all students"         deny  GET students "$ST"

# Teaching assignments and clubs
check "lecturer reads own assignments"     allow GET "teachingAssignments/$LU" "$LT"
check "student reads teachingAssignments"  deny  GET "teachingAssignments/$LU" "$ST"

# Teaching classes added by the lecturer.
assignment() { echo '{"courseId":"'$1'","groupId":"'$2'","semester":"September 2026 – February 2027","createdAt":'"$TS$3"'}'; }
check "lecturer adds own class"            allow PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2405B" "$LT" "$(assignment CS240_ICT602 CDCS2405B)"
check "lecturer writes another lecturer's node" deny PUT "teachingAssignments/$LU2/CS240_ICT602_CDCS2405B" "$LT" "$(assignment CS240_ICT602 CDCS2405B)"
check "student writes teachingAssignments" deny  PUT "teachingAssignments/$SU/CS240_ICT602_CDCS2405B" "$ST" "$(assignment CS240_ICT602 CDCS2405B)"
check "key not equal to courseId_groupId"  deny  PUT "teachingAssignments/$LU/zzTest_wrongkey" "$LT" "$(assignment CS240_ICT602 CDCS2405C)"
check "course from another programme"      deny  PUT "teachingAssignments/$LU/CS230_ENT600_CDCS2405C" "$LT" "$(assignment CS230_ENT600 CDCS2405C)"
check "course from another part"           deny  PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2404A" "$LT" "$(assignment CS240_ICT602 CDCS2404A)"
# A course can sit in more than one part now (courses/{id}/parts). CS259 ACC407 is in 2, 3 and 5.
check "CS259 ACC407 to a Part 3 group"     allow PUT "teachingAssignments/$LU/CS259_ACC407_CDCS2593C" "$LT" "$(assignment CS259_ACC407 CDCS2593C)"
check "CS259 ACC407 to a Part 4 group"     deny  PUT "teachingAssignments/$LU/CS259_ACC407_CDCS2594A" "$LT" "$(assignment CS259_ACC407 CDCS2594A)"
check "CS230 ISP610 (Big Data track) to Part 6" allow PUT "teachingAssignments/$LU/CS230_ISP610_CDCS2306A" "$LT" "$(assignment CS230_ISP610 CDCS2306A)"
check "unknown course"                     deny  PUT "teachingAssignments/$LU/zzTest_NOPE_CDCS2405C" "$LT" "$(assignment zzTest_NOPE CDCS2405C)"
check "unknown group"                      deny  PUT "teachingAssignments/$LU/CS240_ICT602_zzTest_NOPE" "$LT" "$(assignment CS240_ICT602 zzTest_NOPE)"
check "extra field"                        deny  PUT "teachingAssignments/$LU/CS240_ICT602_CDCS2405C" "$LT" \
  "$(assignment CS240_ICT602 CDCS2405C ',"room":"zzTest"')"
check "lecturer removes own class"         allow DELETE "teachingAssignments/$LU/CS240_ICT602_CDCS2405B" "$LT"
check "lecturer reads clubAdvisors"        allow GET clubAdvisors "$LT"
check "student reads clubAdvisors"         deny  GET clubAdvisors "$ST"
# My Clubs: both student indexes change together; advisors only themselves
check "student joins a club (both indexes)" allow PATCH "" "$ST" '{"studentClubs/'$SU'/CSCLUB":true,"clubMembers/CSCLUB/'$SU'":true}'
check "student joins, studentClubs only"   deny  PATCH "" "$ST" '{"studentClubs/'$SU'/NETS":true}'
check "student leaves a club (both indexes)" allow PATCH "" "$ST" '{"studentClubs/'$SU'/CSCLUB":null,"clubMembers/CSCLUB/'$SU'":null}'
check "student removes another member"     deny  PATCH "" "$ST" '{"clubMembers/ITEC/'$SU2'":null}'
check "lecturer starts advising a club"    allow PATCH "" "$LT" '{"clubAdvisors/CSCLUB/'$LU'":true}'
check "lecturer stops advising a club"     allow PATCH "" "$LT" '{"clubAdvisors/CSCLUB/'$LU'":null}'
check "lecturer removes another advisor"   deny  PATCH "" "$LT2" '{"clubAdvisors/ITEC/'$LU'":null}'
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
# Test accounts never mix with real ones (data/TestWorld)
check "test lecturer session without isTest"  deny  PATCH "" "$TLT" "$(new_session zzTest_ts2 CDCS2405A $TLU)"
check "test lecturer session with isTest"     allow PATCH "" "$TLT" "$(new_session zzTest_ts3 CDCS2405A $TLU ',"isTest":true')"
check "real lecturer session with isTest"     deny  PATCH "" "$LT" "$(new_session zzTest_s4 CDCS2405A $LU ',"isTest":true')"
check "tester checks in to a real session"    deny  PATCH "" "$TST" "$(checkin $TSU $SID)"
check "real student checks in to test session" deny PATCH "" "$ST" "$(checkin $SU $TSID)"
check "real lecturer marks tester manually"   deny  PATCH "" "$LT" '{"attendance/'$SID'/'$TSU'":{"status":"present","time":'$TS',"method":"manual","reason":"zzTest","markedBy":"'$LU'"}}'
check "tester checks in to test session"      allow PATCH "" "$TST" "$(checkin $TSU $TSID)"
check "student reads own mirror"           allow GET "studentAttendance/$SU" "$ST"
# studentAttendance must carry a status that matches an attendance record written in the same update
# (zzTest_none is a session id that does not exist).
check "mirror with no status"              deny  PUT "studentAttendance/$SU/zzTest_none" "$ST" '{"time":'$TS'}'
check "mirror without a matching record"   deny  PUT "studentAttendance/$SU/zzTest_none" "$ST" '{"status":"present","time":'$TS'}'
check "mirror for someone else"            deny  PUT "studentAttendance/$LU/zzTest_none" "$ST" '{"status":"present","time":'$TS'}'


# Delete closed sessions (lecturer) and hide sessions (student).
delete_paths() {
  local s='{"sessions/'$1'":null,"sessionsByLecturer/'$2'/'$1'":null,"sessionsByGroup/CDCS2405A/'$1'":null,"attendance/'$1'":null,"attempts/'$1'":null'
  [ -n "$3" ] && s=$s',"studentAttendance/'$3'/'$1'":null'
  [ -n "$4" ] && s=$s',"notifications/'$3'/'$4'":null'
  echo "$s}"
}
q_session() { QS="&orderBy=%22sessionId%22&equalTo=%22$1%22"; }
q_session $CSID
check "lecturer queries notifications of own session" allow GET "notifications/$SU" "$LT"
q_session $CSID2
check "lecturer queries notifications of other's session" deny GET "notifications/$SU" "$LT"
check "lecturer deletes own OPEN session"         deny  PATCH "" "$LT" "$(delete_paths $SID $LU $SU zzTest_n2)"
check "lecturer deletes attempts of open session" deny  DELETE "attempts/$SID" "$LT"
check "lecturer deletes another lecturer's session" deny PATCH "" "$LT" "$(delete_paths $CSID2 $LU2)"
check "student deletes a closed session"          deny  DELETE "sessions/$CSID3" "$ST"
check "student deletes attendance"                deny  DELETE "attendance/$CSID3" "$ST"
check "student deletes own attendance mirror"     deny  DELETE "studentAttendance/$SU/$CSID3" "$ST"
check "lecturer deletes own closed session"       allow PATCH "" "$LT" "$(delete_paths $CSID $LU $SU zzTest_n1)"
check "student hides closed session"              allow PUT "hiddenSessions/$SU/$CSID3" "$ST" 'true'
check "student hides open session"                deny  PUT "hiddenSessions/$SU/$SID" "$ST" 'true'
check "student hides with a value other than true" deny PUT "hiddenSessions/$SU/$CSID3" "$ST" '"yes"'
check "student reads another student's hidden list" deny GET "hiddenSessions/$SU" "$ST2"
check "student un-hides a session"                allow DELETE "hiddenSessions/$SU/$CSID3" "$ST"
check "student deletes own notification"          allow DELETE "notifications/$SU/zzTest_n2" "$ST"
check "student deletes another's notification"    deny  DELETE "notifications/$SU2/zzTest_n3" "$ST"

echo "----"; echo "$pass passed, $fail failed"
[ "$fail" = 0 ]
