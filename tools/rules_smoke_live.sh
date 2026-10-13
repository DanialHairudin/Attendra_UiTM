#!/usr/bin/env bash
# READ-ONLY smoke test of the LIVE attendra-uitm database, run after publishing database.rules.json.
P="${1:-.}"
DB=https://attendra-uitm-default-rtdb.asia-southeast1.firebasedatabase.app
KEY=$(grep -o '"current_key": *"[^"]*"' "$P/app/google-services.json" | head -1 | sed 's/.*: *"\(.*\)"/\1/')
prop() { grep "^$1=" "$P/local.properties" | cut -d= -f2- | tr -d '\r'; }
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
{ [ -z "$ST" ] || [ -z "$LT" ]; } && { echo "sign-in failed"; exit 1; }

pass=0; fail=0
# check <name> <expect allow|deny> <path> [token] (GET only, by design)
check() {
  local name=$1 expect=$2 path=$3 tok=$4 url code
  url="$DB/$path.json?shallow=true"; [ -n "$tok" ] && url="$url&auth=$tok"
  code=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$url")
  local got=deny; [ "$code" = 200 ] && got=allow
  if [ "$got" = "$expect" ]; then pass=$((pass+1)); echo "PASS  $name ($code)";
  else fail=$((fail+1)); echo "FAIL  $name: expected $expect, got $got ($code)"; fi
}

SID=$(curl -s -X GET "$DB/sessionsByLecturer/$LU.json?auth=$LT&shallow=true" | grep -o '"[^"]*":true' | head -1 | cut -d'"' -f2)
echo "student=$SU lecturer=$LU session=${SID:-none}"

check "signed out reads programmes"        allow programmes
check "signed out reads courses"           deny  courses
check "signed out reads students"          deny  students
check "student reads buildings"            allow buildings "$ST"
check "student reads own role"             allow "roles/$SU" "$ST"
check "student reads own profile"          allow "students/$SU" "$ST"
check "student reads lecturer profile"     deny  "lecturers/$LU" "$ST"
check "student reads all students"         deny  students "$ST"
check "lecturer reads own assignments"     allow "teachingAssignments/$LU" "$LT"
check "student reads teachingAssignments"  deny  "teachingAssignments/$LU" "$ST"
check "student reads clubAdvisors"         deny  clubAdvisors "$ST"
check "student reads studentsByGroup"      deny  "studentsByGroup/CDCS2405A" "$ST"
check "student reads own notifications"    allow "notifications/$SU" "$ST"
check "lecturer reads student notifications" deny "notifications/$SU" "$LT"
if [ -n "$SID" ]; then
  check "student reads a session"          allow "sessions/$SID" "$ST"
  check "lecturer reads own session attendance" allow "attendance/$SID" "$LT"
  check "student reads whole attendance"   deny  "attendance/$SID" "$ST"
  check "student reads attempts"           deny  "attempts/$SID" "$ST"
fi

echo "----"; echo "$pass passed, $fail failed"
[ "$fail" = 0 ]
