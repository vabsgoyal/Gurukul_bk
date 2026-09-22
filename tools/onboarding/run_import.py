"""
Registers Balaji Vidhyapeeth Unhel and bulk-imports its clean student roster.

Already run successfully once, against local H2 (see spec.md Outcome) - 358/358 succeeded. This copy
is for running it again against a real environment (e.g. production) from a machine that can actually
reach it, since the environment this was built in cannot (see TODOS.md's CRITICAL/production-import
Backlog items for why).

Usage:
    pip install requests
    GURUKUL_BASE_URL=http://13.60.11.238:8080 python run_import.py

Defaults to http://localhost:8080 if GURUKUL_BASE_URL is unset. Requires Gurukul_bk#93 (student RTE
fields + POST /api/v1/students/bulk) to already be deployed to whatever BASE points at - running this
against a target that doesn't have that code yet will fail at the class-section/bulk-import step.

Safe to re-run against the SAME already-populated target: school registration will fail loudly
(duplicate) rather than silently duplicating the school. It will NOT detect an already-imported
roster on its own, though - don't run this twice against a target that already has this school's
students unless you want 716 students instead of 358.
"""

import json
import os
import sys
import io
import requests

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

BASE = os.environ.get("GURUKUL_BASE_URL", "http://localhost:8080")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
STUDENTS_JSON = os.path.join(SCRIPT_DIR, "students.json")
REPORT_JSON = os.path.join(SCRIPT_DIR, "import_report.json")

with open(STUDENTS_JSON, encoding="utf-8") as f:
    records = json.load(f)

# The two phone numbers the user gave - principalPhone/adminPhone must differ, and these are exactly
# the two numbers provided, in the order given.
PRINCIPAL_PHONE = "9400626016"
ADMIN_PHONE = "7024274770"

print(f"Target: {BASE}")

# 1. Register the school
school_payload = {
    "name": "Balaji Vidhyapeeth Unhel",
    "address": "Balaji Vidhyapeeth Unhel",
    "city": "Unhel",
    "state": "Madhya Pradesh",
    "pincode": "456221",
    "contactEmail": "rajeshpatidar@gmail.com",
    "contactPhone": PRINCIPAL_PHONE,
    "principalName": "Rajesh Patidar",
    "directorName": "Rajesh Patidar",
    "principalPhone": PRINCIPAL_PHONE,
    "adminPhone": ADMIN_PHONE,
}
r = requests.post(f"{BASE}/api/v1/schools", json=school_payload, timeout=30)
resp = r.json()
print("Register school:", r.status_code, resp.get("success"))
if not resp.get("success"):
    print("FAILED:", resp)
    sys.exit(1)
school = resp["data"]["school"]
school_id = school["id"]
print("School ID:", school_id)

headers = {"X-School-Id": school_id}

# 2. OTP login as admin.
#    NOTE: on real environments this is a REAL OTP sent by SMS, not the dummy "1234" local dev uses
#    (see docs/security/SECURITY_AND_ACCESS.md 9.1 - production still has the dummy-OTP gap as of this
#    writing, so "1234" may still work, but don't assume it silently - if this step fails, that's why).
r = requests.post(f"{BASE}/api/v1/auth/otp/request", json={"phone": ADMIN_PHONE}, headers=headers, timeout=30)
print("OTP request:", r.status_code, r.json())

otp = os.environ.get("GURUKUL_ADMIN_OTP", "1234")
r = requests.post(f"{BASE}/api/v1/auth/otp/verify", json={"phone": ADMIN_PHONE, "otp": otp}, headers=headers, timeout=30)
verify_resp = r.json()
print("OTP verify:", r.status_code, verify_resp.get("success"), "role=", verify_resp.get("data", {}).get("role"))
if not verify_resp.get("success"):
    print("FAILED - if this is a real environment with real SMS OTP, set GURUKUL_ADMIN_OTP to the code received:", verify_resp)
    sys.exit(1)
token = verify_resp["data"]["token"]
auth_headers = {**headers, "Authorization": f"Bearer {token}"}

# 3. Create one class-section per grade present among clean, active, importable rows
clean = [r for r in records if r["status"] == "ACTIVE" and r["dob"] and r["parentContact"]]
skipped = [r for r in records if r["status"] == "ACTIVE" and not (r["dob"] and r["parentContact"])]
withdrawn = [r for r in records if r["status"] == "WITHDRAWN"]

grades_needed = sorted({r["gradeName"] for r in clean})
ACADEMIC_YEAR = "2026-27"
grade_to_section_id = {}
for grade in grades_needed:
    payload = {"className": grade, "section": "A", "academicYear": ACADEMIC_YEAR}
    r = requests.post(f"{BASE}/api/v1/class-sections", json=payload, headers=headers, timeout=30)
    resp = r.json()
    if not resp.get("success"):
        print(f"Class-section create FAILED for {grade}: {resp}")
        sys.exit(1)
    grade_to_section_id[grade] = resp["data"]["id"]
print(f"Created {len(grade_to_section_id)} class-sections: {grades_needed}")


def to_student_request(rec):
    s = rec["sensitive"]
    return {
        "name": rec["name"],
        "dob": rec["dob"],
        "gender": rec["gender"],
        "address": rec["address"],
        "parentName": rec["parentName"],
        "parentContact": rec["parentContact"],
        "classSectionId": grade_to_section_id[rec["gradeName"]],
        "admissionDate": rec["admissionDate"],
        "sssmId": s["sssmId"],
        "aadhaarNumber": s["aadhaarNumber"],
        "caste": s["caste"],
        "category": s["category"],
        "annualIncome": int(s["annualIncome"]) if s["annualIncome"] else None,
        "previousSchoolName": s["previousSchoolName"],
        "bankAccountNumber": s["bankAccountNumber"],
        "bankIfsc": s["bankIfsc"],
    }


# 4. Bulk-import the clean rows
bulk_payload = {"students": [to_student_request(r) for r in clean]}
r = requests.post(f"{BASE}/api/v1/students/bulk", json=bulk_payload, headers=auth_headers, timeout=300)
print("Bulk import HTTP:", r.status_code)
bulk_resp = r.json()
if not bulk_resp.get("success"):
    print("BULK IMPORT FAILED:", bulk_resp)
    sys.exit(1)
data = bulk_resp["data"]
print(f"Bulk import: total={data['total']} succeeded={data['succeeded']} failed={data['failed']}")

failures = [row for row in data["results"] if not row["success"]]
if failures:
    print(f"\n{len(failures)} rows failed during bulk import (unexpected - investigate):")
    for row in failures[:20]:
        print(f"  - [{row['index']}] {row['name']}: {row['error']}")

report = {
    "target": BASE,
    "schoolId": school_id,
    "classSections": grade_to_section_id,
    "bulkImport": {"total": data["total"], "succeeded": data["succeeded"], "failed": data["failed"]},
    "bulkImportFailures": failures,
    "skippedForBadData": [
        {"rollNumber": r["rollNumber"], "name": r["name"], "dob": r["dob"], "parentContact": r["parentContact"]}
        for r in skipped
    ],
    "withdrawnDeferred": len(withdrawn),
}
with open(REPORT_JSON, "w", encoding="utf-8") as f:
    json.dump(report, f, indent=2, ensure_ascii=False)
print(f"\nReport written to {REPORT_JSON}")

# 5. Spot-check one record with sensitive fields, as admin vs unauthenticated
if data["results"]:
    sample_id = data["results"][0]["studentId"]
    r = requests.get(f"{BASE}/api/v1/students/{sample_id}", headers=auth_headers, timeout=30)
    admin_view = r.json()["data"]
    print("\nSpot check (admin):", {k: admin_view.get(k) for k in ["name", "aadhaarNumber", "caste", "bankAccountNumber", "registrationNumber"]})

    r = requests.get(f"{BASE}/api/v1/students/{sample_id}", headers=headers, timeout=30)
    anon_view = r.json()["data"]
    print("Spot check (no auth):", {k: anon_view.get(k) for k in ["name", "aadhaarNumber", "caste", "bankAccountNumber", "registrationNumber"]})
