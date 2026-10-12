#!/usr/bin/env bash
# READ-ONLY (GET only): shows the live paths the old rules_test.sh may have
# written while the permissive dev rules were published. Changes nothing.
# Usage (Git Bash, from the project folder): bash tools/find_test_junk_live.sh .
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
echo "student=$SU lecturer=$LU"
g() { echo "== $1"; curl -s -X GET "$DB/$1.json?auth=$2"; echo; }

g buildings/CS9 "$LT"
g roles/$SU "$ST"
g students/$SU/isTest "$ST"
g students/$SU/groupId "$ST"
g students/$SU/emailVerified "$ST"
g studentsByGroup/CDCS2405A/$SU "$LT"
g studentsByGroup/CDCS2405B/$SU "$LT"
g teachingAssignments/$LU "$LT"
g sessions/zzrulestest "$LT"
g sessionsByLecturer/$LU "$LT"
g studentAttendance/$SU "$ST"
g studentAttendance/$LU/zzrulestest "$LT"
for sid in $(curl -s -X GET "$DB/sessionsByLecturer/$LU.json?auth=$LT&shallow=true" | grep -o '"[^"]*":true' | cut -d'"' -f2); do
  g sessions/$sid/title "$LT"
  g attendance/$sid/$SU "$LT"
  g attempts/$sid "$LT"
done
