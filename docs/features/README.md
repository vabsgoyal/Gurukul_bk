# Feature docs

Flow walkthroughs and test plans for money-related features, written directly from the
current backend (`com.gurukul.fees`, `com.gurukul.payroll`, `com.gurukul.expenses.infrastructure`,
`com.gurukul.reports`) and frontend (`Gurukul_rn/src/screens/principal`) code — not a design spec.
Each feature has a `FLOW.md` (overview, actors, diagram, endpoints, data model, known limitations)
and a `TEST.md` (manual checklist, existing automated coverage, suggested gaps).

- [`fee-structure-setup/`](./fee-structure-setup/FLOW.md) — Fee categories, fee structures, and bulk bill (`StudentFeeAssessment`) generation.
- [`fee-payment/`](./fee-payment/FLOW.md) — Manual fee payment recording, and the built-but-disabled online UPI payment attempt flow.
- [`fees-overview/`](./fees-overview/FLOW.md) — The admin's school-wide fee assessments list and a teacher's own-section "My Class Fees" view.
- [`payroll-runs/`](./payroll-runs/FLOW.md) — Salary structures and the payroll run lifecycle (Draft → Processed → Paid).
- [`payroll-overview/`](./payroll-overview/FLOW.md) — The paid/pending payroll rollup across every run.
- [`infra-expenses/`](./infra-expenses/FLOW.md) — Infrastructure expense request → approve/reject → purchase → pay workflow.
