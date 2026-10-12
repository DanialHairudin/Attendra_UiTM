#!/usr/bin/env bash
# C12 rules check over the REST API, as the test student and test lecturer.
# Usage (Git Bash, from the project folder): bash tools/rules_test.sh .
# Reads the test accounts from local.properties and the API key from
# app/google-services.json. Run it only AFTER publishing database.rules.json.
# Every case prints PASS/FAIL against what database.rules.json should allow.
P="$1"
DB=https://attendra-uitm-default-rtdb.asia-southeast1.firebasedatabase.app
KEY=$(grep -o '"current_key": *"[^"]*"' "$P/app/google-services.json" | head -1 | sed 's/.*: *"\(.*\)"/\1/')
prop() { grep "^$1=" "$P/local.properties" | cut -d= -f2-; }
signin() {
  curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$KEY" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"returnSecureToken\":true}"
}
field() { grep -o "\"$1\": *\"[^\"]*\"" | sed 's/.*: *"\(.*\)"/\1/'; }

S_JSON=$(signin "$(prop TEST_STUDENT_EMAIL)" "$(prop TEST_STUDENT_PASS)")
L_JSON=$(signin "$(prop TEST_LECTURER_EMAIL)" "$(prop TEST_LECTURER_PASS)")
ST=$(echo "$S_JSON" | field idToken); SU=$(echo "$S_JSON" | field localId)
LT=$(echo "$L_JSON" | field idToken); LU=$(echo "$L_JSON" | field localId)
[ -z "$ST" ] || [ -z "$LT" ] && { echo "sign-in failed"; exit 1; }

pass=0; fail=0
# check <name> <expect allow|deny> <method> <path> [token] [body]
check() {
  local name=$1 expect=$2 method=$3 path=$4 tok=$5 body=$6 url code
  url="$DB/$path.json"; [ -n "$tok" ] && url="$url?auth=$tok"
  if [ -n "$body" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" --data-binary "$body")
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url")
  fi
  local got=deny; [ "$code" = 200 ] && got=allow
  if [ "$got" = "$expect" ]; then pass=$((pass+1)); echo "PASS  $name ($code)";
  else fail=$((fail+1)); echo "FAIL  $name: expected $expect, got $got ($code)"; fi
}

SID=$(curl -s "$DB/sessionsByLecturer/$LU.json?auth=$LT&shallow=true" | grep -o '"[^"]*":true' | head -1 | cut -d'"' -f2)
echo "student=$SU lecturer=$LU session=$SID"

# SAFETY: the "deny" cases below really write if the rules allow them. Probe
# first with a write that changes nothing (the role is already "student"):
# the C12 rules refuse it (roles are write-once); the old dev rules allow it.
# If it is allowed, the C12 rules are not published yet, so stop here.
canary=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$DB/roles/$SU.json?auth=$ST" --data-binary '"student"')
if [ "$canary" = 200 ]; then
  echo "STOP: the C12 rules are not published (a role rewrite was allowed). Nothing else was run."
  exit 2
fi

# Seed data
check "signed out reads programmes"        allow GET programmes
check "signed out reads courses"           deny  GET courses
check "student reads buildings"            allow GET buildings "$ST"
check "student writes buildings"           deny  PATCH buildings "$ST" '{"CS9":{"name":"x"}}'

# Accounts
check "student reads own role"             allow GET "roles/$SU" "$ST"
check "student changes own role"           deny  PUT "roles/$SU" "$ST" '"lecturer"'
check "student reads own profile"          allow GET "students/$SU" "$ST"
check "student reads lecturer profile"     deny  GET "lecturers/$LU" "$ST"
check "lecturer reads student profile"     allow GET "students/$SU" "$LT"
check "student sets own isTest"            deny  PUT "students/$SU/isTest" "$ST" 'false'
check "student changes own groupId"        deny  PUT "students/$SU/groupId" "$ST" '"CDCS2405B"'
check "student sets emailVerified (no code)" deny PUT "students/$SU/emailVerified" "$ST" 'true'
check "student reads all students"         deny  GET students "$ST"

# Teaching assignments and clubs
check "lecturer reads own assignments"     allow GET "teachingAssignments/$LU" "$LT"
check "lecturer writes own assignments"    deny  PATCH "teachingAssignments/$LU" "$LT" '{"x":{"courseId":"a","groupId":"b"}}'
check "student reads teachingAssignments"  deny  GET "teachingAssignments/$LU" "$ST"
check "lecturer reads clubAdvisors"        allow GET clubAdvisors "$LT"
check "student reads clubAdvisors"         deny  GET clubAdvisors "$ST"
check "student reads studentsByGroup"      deny  GET "studentsByGroup/CDCS2405A" "$ST"
check "lecturer reads studentsByGroup"     allow GET "studentsByGroup/CDCS2405A" "$LT"

# Sessions and attendance
check "student reads a session"            allow GET "sessions/$SID" "$ST"
check "student creates a session"          deny  PUT "sessions/zzrulestest" "$ST" '{"lecturerUid":"'$SU'"}'
check "lecturer deletes own session"       deny  DELETE "sessions/$SID" "$LT"
check "lecturer reads own session attendance" allow GET "attendance/$SID" "$LT"
check "student reads whole attendance"     deny  GET "attendance/$SID" "$ST"
check "student reads own record"           allow GET "attendance/$SID/$SU" "$ST"
check "student overwrites own record"      deny  PATCH "attendance/$SID/$SU" "$ST" '{"status":"present"}'
check "student fakes accepted attempt"     deny  POST "attempts/$SID" "$ST" '{"uid":"'$SU'","time":{".sv":"timestamp"},"result":"accepted","lat":3.07,"lng":101.5}'
check "student reads attempts"             deny  GET "attempts/$SID" "$ST"
check "student reads own notifications"    allow GET "notifications/$SU" "$ST"
check "lecturer reads student notifications" deny GET "notifications/$SU" "$LT"
check "student reads own mirror"           allow GET "studentAttendance/$SU" "$ST"
# studentAttendance must carry a status that matches an attendance record written
# in the same update (zzrulestest is a session id that does not exist).
check "mirror with no status"              deny  PUT "studentAttendance/$SU/zzrulestest" "$ST" '{"time":{".sv":"timestamp"}}'
check "mirror without a matching record"   deny  PUT "studentAttendance/$SU/zzrulestest" "$ST" '{"status":"present","time":{".sv":"timestamp"}}'
check "mirror for someone else"            deny  PUT "studentAttendance/$LU/zzrulestest" "$ST" '{"status":"present","time":{".sv":"timestamp"}}'

echo "----"; echo "$pass passed, $fail failed"
