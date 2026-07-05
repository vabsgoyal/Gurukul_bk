# School Fund Management — HLD & LLD

Paste this content into the [Principle Features Google Doc](https://docs.google.com/document/d/1oLKd0l5kQYeHVgKfjWY0o25cyHuRpnhEip7hDyRoT5o/edit).

## HLD — School Fund Management

### 1. Purpose

Track all school money inflows (student fees, event collections, sponsorships) and outflows (salaries, infrastructure, event expenses) with audit trail, due tracking, approvals, and reporting.

### 2. System Context

- Backend: Gurukul Spring Boot 4.1 (Java 25), JPA, Flyway
- Database: H2 (local), Aurora PostgreSQL (prod)
- Multi-tenant: `X-School-Id` header per school
- Existing modules: Schools, Class-Sections, Students
- Deployment: EC2 + GitHub Actions CI/CD

### 3. Architectural Style

- Vertical slice architecture (one package per domain)
- Shared platform: Finance Ledger, Workflow, Documents, Events, Vendors, Employees
- All money flows through central `FinancialTransaction` table

### 4. Module Map

| Layer | Modules |
|-------|---------|
| Inflow | fees, collections, sponsorships |
| Outflow | payroll, infrastructure expenses, event expenses |
| Platform | finance, workflow, documents, events, vendors, employees |
| Reporting | reports (read-only aggregations) |
| Existing | schools, students |

### 5. High-Level Data Flow

**Inflow:** Domain action → `LedgerService.recordInflow()` → `FinancialTransaction` → receipt number

**Outflow:** Expense request → Workflow (submit/approve) → Purchase → `LedgerService.recordOutflow()` → `FinancialTransaction`

### 6. Delivery Phases (Implemented)

1. Foundation (ledger, vendors, employees, events)
2. Inflow: Student Fees → Event Collections → Sponsorships
3. Outflow: Workflow → Infrastructure → Event Expenses → Payroll
4. Reports

---

## LLD — School Fund Management

### Package Structure

```
com.gurukul/
├── finance/          # Ledger, FundAccount, ReceiptSequence
├── vendors/
├── employees/
├── events/
├── documents/        # ReceiptNumberGenerator
├── fees/
├── collections/
├── sponsorships/
├── workflow/
├── expenses/
│   ├── infrastructure/
│   └── events/
├── payroll/
└── reports/
```

### Core Entity: FinancialTransaction

Central ledger for all monetary movements. Fields: direction (INFLOW/OUTFLOW), sourceType, sourceId, amount, paymentMethod, receiptNumber, status, fundAccountId.

### API Conventions

- Base path: `/api/v1`
- Header: `X-School-Id: {uuid}`
- Response envelope: `ApiResponse<T>` with `{ success, data, message }`

### Migration Sequence

| File | Contents |
|------|----------|
| V2__finance_foundation.sql | fund_account, financial_transaction, receipt_sequence, vendor, employee, school_event |
| V3__student_fees.sql | fee tables |
| V4__event_collections.sql | collection tables |
| V5__sponsorships.sql | sponsorship tables |
| V6__workflow.sql | approval tables |
| V7__infrastructure_expenses.sql | infra expense tables |
| V8__event_expenses.sql | event budget/expense tables |
| V9__payroll.sql | payroll tables |
