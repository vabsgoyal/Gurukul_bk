#!/usr/bin/env bash
#
# Seeds a payable fee assessment + a student login into a LOCAL backend, so the app's Pay Fees
# flow has something to act on.
#
#   ./scripts/seed-demo-fees.sh
#
# Why this exists: the local profile uses an in-memory H2 database
# (application-local.properties), so every backend restart wipes all fee data. The seeded demo
# school and its principal login survive (they come from Flyway migrations) but there are no
# students, fee structures or assessments - and "Pay Fees" needs an assessment to point at.
#
# Safe to re-run: each run creates a fresh class section, fee category and student with a random
# suffix, so nothing collides. Provisioning the student credential is the one step that can fail on
# a re-run (the username is unique per school) - that failure is reported and ignored, since the
# existing login still works.
set -uo pipefail

BASE=${BASE:-http://localhost:8080}
# Seeded by V1__student_school.sql; the principal login comes from V17.
SCHOOL=${SCHOOL:-11111111-1111-1111-1111-111111111111}
ADMIN_USER=${ADMIN_USER:-9999999999}
ADMIN_PASS=${ADMIN_PASS:-Principal@9999}
STUDENT_USER=${STUDENT_USER:-demo.student}
STUDENT_PASS=${STUDENT_PASS:-DemoPass123!}
AMOUNT=${AMOUNT:-10000.00}

if ! curl -sf -o /dev/null "$BASE/actuator/health"; then
  echo "Backend is not answering at $BASE - start it first:" >&2
  echo "  cd backend && set -a; . ./.env.local; set +a; ./mvnw spring-boot:run" >&2
  exit 1
fi

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']$1 if d.get('success') else '')"; }
post()  { curl -s -X POST -H "X-School-Id: $SCHOOL" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$BASE$1" -d "$2"; }

TOKEN=$(curl -s -X POST -H "X-School-Id: $SCHOOL" -H 'Content-Type: application/json' \
  "$BASE/api/v1/auth/login" -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jget "['token']")
[ -z "$TOKEN" ] && { echo "Admin login failed ($ADMIN_USER). Has V17 run?" >&2; exit 1; }

SUF=$RANDOM
SEC=$(post /api/v1/class-sections "{\"className\":\"Grade 8\",\"section\":\"D$SUF\",\"academicYear\":\"2026-27\"}" | jget "['id']")
CAT=$(post /api/v1/fee-categories "{\"code\":\"TUI$SUF\",\"name\":\"Tuition Fee\"}" | jget "['id']")
STU=$(post /api/v1/students "{\"rollNumber\":\"D$SUF\",\"name\":\"Demo Student\",\"dob\":\"2012-01-01\",\"gender\":\"MALE\",\"address\":\"Demo Address\",\"parentName\":\"Demo Parent\",\"parentContact\":\"9876543210\",\"classSectionId\":\"$SEC\",\"admissionDate\":\"2026-04-01\"}" | jget "['id']")
STR=$(post /api/v1/fee-structures "{\"classSectionId\":\"$SEC\",\"academicYear\":\"2026-27\",\"lines\":[{\"feeCategoryId\":\"$CAT\",\"amount\":$AMOUNT}]}" | jget "['id']")
post "/api/v1/fee-structures/$STR/generate-assessments" '' > /dev/null

# The UPI-intent fallback refuses to build a deep link unless the school has bank details, and the
# Razorpay path shows the school name on the checkout sheet. Set them either way.
CUR=$(curl -s -H "X-School-Id: $SCHOOL" "$BASE/api/v1/schools/$SCHOOL" | python3 -c "import sys,json;print(json.dumps(json.load(sys.stdin)['data']))")
BODY=$(CUR="$CUR" python3 -c "
import json,os
d=json.loads(os.environ['CUR'])
d.update(bankAccountNumber='123456789012', bankIfsc='SBIN0001234', bankAccountHolderName='Demo School Fees')
for k in ('id','createdAt','updatedAt'): d.pop(k, None)
print(json.dumps(d))")
curl -s -o /dev/null -X PUT -H "X-School-Id: $SCHOOL" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' "$BASE/api/v1/schools/$SCHOOL" -d "$BODY"

CRED=$(post "/api/v1/students/$STU/credentials" "{\"username\":\"$STUDENT_USER\",\"password\":\"$STUDENT_PASS\",\"role\":\"STUDENT\"}")
echo "$CRED" | grep -q '"success":true' \
  || echo "note: could not create the $STUDENT_USER login (likely already exists from a previous run) - the existing one still works, but it belongs to an EARLIER student, so log in and check My Fees to see which assessment you get."

ASS=$(curl -s -H "X-School-Id: $SCHOOL" -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/students/$STU/fee-assessments" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")

cat <<EOF

Seeded.
  School        Gurukul Demo School  ($SCHOOL)
  Student       Demo Student  (roll D$SUF)
  Assessment    ${ASS:-<none - check the fee structure step>}   Rs $AMOUNT  UNPAID

  Student login $STUDENT_USER / $STUDENT_PASS
  Admin login   $ADMIN_USER / $ADMIN_PASS

In the app: Find my school -> Gurukul Demo School -> "Sign in with username & password instead"
            -> My Fees -> the UNPAID row -> Pay Fees
EOF
