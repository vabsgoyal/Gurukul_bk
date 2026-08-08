# Gurukul Figma Design Document

## 1. Product Overview

Gurukul is a multi-tenant school management platform. The current backend supports school registration, class-section management, and student enrollment. The product design should support two layers:

- **Administrative backend workflows** for schools to register, manage class sections, and enroll students.
- **Student-first experience** for future daily usage: timetable, assignments, grades, AI study companion, portfolio, wellness, offline resources, and academic credentials.

The Figma file should be structured so the current backend APIs can be tested visually now, while future student-facing screens can be added without redesigning the whole system.

## 2. Figma File Structure

Create these Figma pages:

1. **00 Cover**
2. **01 Design Tokens**
3. **02 Components**
4. **03 Admin Web App**
5. **04 Student Mobile App**
6. **05 Prototype Flows**
7. **06 API Mapping**
8. **07 Future Roadmap**

## 3. Target Users

### School Admin

Primary goal: set up and manage a school tenant.

Daily needs:

- Register school profile
- View school summary
- Create class sections
- Enroll students
- Search and manage student records
- Use the `X-School-Id` tenant context safely

### Teacher

Primary goal: manage class-level academic operations.

Future needs:

- View class sections
- See student lists
- Review attendance, assignments, grades
- Moderate discussion spaces

### Student

Primary goal: use the app as a personal academic companion.

Future needs:

- Know what is due today
- Submit assignments
- Track attendance and grades
- Use AI study help
- Build achievement portfolio
- Access resources offline
- Use regional language and voice support

## 4. Design Principles

- **Operational clarity first**: admin workflows should be dense, clear, and fast.
- **Student app feels personal**: the student side should feel like a daily companion, not an administrative portal.
- **Multi-tenant safety**: make the current school context visible wherever data can be modified.
- **Low visual noise**: use restrained cards and tables for admin screens.
- **Accessible by default**: clear labels, strong contrast, keyboard-friendly forms, and readable error states.
- **Mobile-first for students**: students are more likely to use phones than desktop dashboards.

## 5. Design Tokens

### Colors

Use a balanced education-focused palette, not a single-color theme.

| Token | Hex | Usage |
|---|---:|---|
| `color.primary` | `#2563EB` | Primary actions, selected navigation |
| `color.primary.dark` | `#1D4ED8` | Hover/pressed primary |
| `color.success` | `#15803D` | Success states |
| `color.warning` | `#B45309` | Pending, alerts |
| `color.danger` | `#DC2626` | Destructive actions, validation |
| `color.info` | `#0891B2` | Informational badges |
| `color.bg` | `#F8FAFC` | App background |
| `color.surface` | `#FFFFFF` | Panels, forms, table surface |
| `color.border` | `#E2E8F0` | Borders and dividers |
| `color.text` | `#0F172A` | Main text |
| `color.text.muted` | `#64748B` | Secondary text |

### Typography

Use Inter, SF Pro, or a similar neutral UI font.

| Style | Size | Weight | Line height | Usage |
|---|---:|---:|---:|---|
| Display | 32 | 700 | 40 | Page headers only |
| H1 | 24 | 700 | 32 | Main screen title |
| H2 | 20 | 650 | 28 | Section title |
| Body | 14 | 400 | 20 | Default text |
| Body Strong | 14 | 600 | 20 | Table values, labels |
| Caption | 12 | 400 | 16 | Helper text, metadata |

### Spacing

Use an 8px grid.

| Token | Value |
|---|---:|
| `space.1` | 4 |
| `space.2` | 8 |
| `space.3` | 12 |
| `space.4` | 16 |
| `space.5` | 24 |
| `space.6` | 32 |
| `space.7` | 48 |

### Radius

| Token | Value | Usage |
|---|---:|---|
| `radius.sm` | 4 | Inputs, badges |
| `radius.md` | 8 | Cards, dialogs |
| `radius.full` | 999 | Pills, avatars |

## 6. Component Library

### Navigation

Components:

- Admin sidebar
- Top app bar
- Breadcrumbs
- Student bottom navigation
- Mobile top header

Admin sidebar items:

- Dashboard
- Schools
- Class Sections
- Students
- API Docs
- Settings

Student bottom nav items:

- Home
- Study
- Tasks
- Portfolio
- Profile

### Buttons

Variants:

- Primary
- Secondary
- Ghost
- Danger
- Icon only

States:

- Default
- Hover
- Pressed
- Disabled
- Loading

### Forms

Components:

- Text input
- Email input
- Phone input
- Select
- Date picker
- Text area
- Field group
- Inline validation
- Required marker

### Data Display

Components:

- Data table
- Empty state
- Loading skeleton
- Status badge
- Count metric
- Detail row
- Audit metadata row

### Feedback

Components:

- Toast
- Inline alert
- Confirmation modal
- Error summary

### Student-Specific Components

Components:

- Today card
- Assignment due item
- Subject progress chip
- AI tutor prompt card
- Wellness check-in selector
- Portfolio achievement card
- Offline sync indicator
- Timetable period block

## 7. Admin Web App Screens

Recommended desktop frame: **1440 x 1024**

### 7.1 Admin Dashboard

Purpose: give school operators a quick operational snapshot.

Layout:

- Left sidebar
- Top bar with active school context
- Main content area

Sections:

- School summary
- Student count
- Class-section count
- Teacher count placeholder
- Recent students
- Quick actions

Quick actions:

- Register school
- Create class section
- Enroll student
- Open Swagger UI

Empty state:

If no school is registered, show a setup panel with one primary action: `Register School`.

### 7.2 Register School

API mapping:

- `POST /api/v1/schools`

Form fields:

- School name
- Address
- City
- State
- Pincode
- Contact email
- Contact phone
- Principal name
- Director name

Design behavior:

- Show field-level validation.
- On success, show the created school UUID.
- Include a copy button for `X-School-Id`.
- Explain that this ID is required for tenant-scoped APIs.

Primary action:

- `Register School`

Secondary action:

- `Cancel`

### 7.3 School Detail

API mapping:

- `GET /api/v1/schools/{id}`

Sections:

- School profile
- School UUID
- Contact details
- Principal/director
- Student count
- Class-section count
- Teacher count placeholder

Important UI detail:

The school UUID should be easy to copy because it maps to the backend `X-School-Id` header.

### 7.4 Class Sections

Purpose: manage grade, section, and academic year combinations.

Likely API surface:

- `GET /api/v1/class-sections`
- `POST /api/v1/class-sections`
- `GET /api/v1/class-sections/{id}/students`

Table columns:

- Class name
- Section
- Academic year
- Student count
- Created at
- Actions

Create form fields:

- Class name
- Section
- Academic year

States:

- Empty class-section list
- Duplicate class-section error
- Missing `X-School-Id` header error

### 7.5 Students List

API mapping:

- `GET /api/v1/students`
- `GET /api/v1/students/by-class-section`

Table columns:

- Roll number
- Student name
- Class section
- Gender
- Status
- Parent/guardian contact
- Created at
- Actions

Filters:

- Class name
- Section
- Academic year
- Status
- Search by name or roll number

Actions:

- View
- Edit
- Transfer class section
- Delete

### 7.6 Enroll Student

API mapping:

- `POST /api/v1/students`

Form groups:

- Student identity
- Class assignment
- Guardian details
- Contact details
- Status

Design behavior:

- Roll number uniqueness errors should be shown near the roll number input.
- Class-section selection should only show sections from the current school.
- Success state should show the student ID and link to student detail.

### 7.7 Student Detail

API mapping:

- `GET /api/v1/students/{id}`
- `PUT /api/v1/students/{id}`
- `PATCH /api/v1/students/{id}/class-section`
- `DELETE /api/v1/students/{id}`

Sections:

- Profile header
- Class section
- Guardian/contact details
- Status
- System metadata

Actions:

- Edit student
- Transfer class section
- Delete student

Delete confirmation:

- Require explicit confirmation.
- Show warning that deletion is permanent.

## 8. Student Mobile App Screens

Recommended mobile frame: **390 x 844**

These screens are roadmap-ready and based on the intended student-side product direction.

### 8.1 Student Home

Purpose: daily student command center.

Sections:

- Greeting and current class
- Today timeline
- Upcoming assignment
- AI study companion
- Progress snapshot
- Wellness check-in
- Offline status

Primary actions:

- Ask AI tutor
- Submit assignment
- View timetable
- Check grades

### 8.2 Study Companion

Purpose: personalized AI study support.

Features:

- Suggested weak topics
- Recent test-based revision
- Ask a doubt
- Generate practice questions
- Voice input
- Language switch

Important design rule:

The AI should show what it used: syllabus topic, teacher note, textbook chapter, or past test result.

### 8.3 Tasks

Purpose: track homework and assignments.

Sections:

- Due today
- Due this week
- Submitted
- Feedback received

States:

- Draft saved offline
- Pending sync
- Submitted
- Returned with feedback

### 8.4 Portfolio

Purpose: long-term academic and co-curricular profile.

Sections:

- Certificates
- Projects
- Competitions
- Badges
- Skills
- Shareable profile

Actions:

- Add project
- Share portfolio
- Export PDF

### 8.5 Wellness Check-In

Purpose: lightweight, private student wellbeing check-in.

Inputs:

- Mood
- Stress level
- Energy level
- Optional note

Privacy text:

Keep it short. Do not make the screen feel clinical or punitive.

Design behavior:

- Show reassurance that patterns may be shared with counselors for support.
- Do not connect wellness directly to grades.

### 8.6 Profile

Sections:

- Digital ID
- Attendance
- Grades
- Fee status
- Certificates
- APAAR/digital academic identity
- Language preference
- Offline downloads

## 9. Prototype Flows

### Flow A: Admin Registers a School

1. Open Admin Dashboard.
2. Click `Register School`.
3. Fill school registration form.
4. Submit.
5. Show success screen with school UUID.
6. Copy `X-School-Id`.
7. Navigate to class-section setup.

### Flow B: Admin Enrolls a Student

1. Open Students.
2. Click `Enroll Student`.
3. Fill student details.
4. Select class section.
5. Submit.
6. Show student detail page.

### Flow C: Admin Transfers Student Class Section

1. Open student detail.
2. Click `Transfer`.
3. Select new class section.
4. Confirm.
5. Show updated class-section badge.

### Flow D: Student Opens Daily Home

1. Student opens app.
2. Sees today timetable and pending assignment.
3. Taps AI tutor suggestion.
4. Completes practice.
5. Streak/progress updates.

## 10. API Mapping for Figma Annotations

Use these API notes in Figma dev mode or annotations.

| Screen | API |
|---|---|
| Register School | `POST /api/v1/schools` |
| School Detail | `GET /api/v1/schools/{id}` |
| Class Sections | `GET /api/v1/class-sections` |
| Create Class Section | `POST /api/v1/class-sections` |
| Students List | `GET /api/v1/students` |
| Students by Class Section | `GET /api/v1/students/by-class-section` |
| Student Detail | `GET /api/v1/students/{id}` |
| Enroll Student | `POST /api/v1/students` |
| Update Student | `PUT /api/v1/students/{id}` |
| Transfer Student | `PATCH /api/v1/students/{id}/class-section` |
| Delete Student | `DELETE /api/v1/students/{id}` |

All tenant-scoped APIs require:

```text
X-School-Id: 11111111-1111-1111-1111-111111111111
```

Exceptions:

- `POST /api/v1/schools`
- `GET /api/v1/schools/{id}`

## 11. Error States

Design these explicitly:

- Missing `X-School-Id`
- Invalid `X-School-Id`
- School not found
- Student not found
- Class-section not found
- Duplicate roll number
- Duplicate class section
- Validation failed
- Network error
- Empty list
- Loading state

## 12. Figma Naming Convention

Use predictable names:

```text
Page / Section / Frame
Admin / Students / List
Admin / Students / Enroll
Admin / Schools / Register
Student / Home / Default
Student / Tasks / Offline Draft
Component / Button / Primary
Component / Input / Error
```

## 13. Handoff Notes

For every final screen, include:

- Frame name
- User role
- API endpoint
- Required headers
- Empty state
- Loading state
- Error state
- Success state
- Responsive notes

For admin web screens:

- Desktop first
- Minimum width: 1024px
- Use tables for repeated records
- Keep primary actions in the page header

For student screens:

- Mobile first
- Bottom navigation
- Large tap targets
- Offline and sync status always visible when relevant

## 14. Recommended First Figma Deliverable

Start with these frames:

1. Cover
2. Design tokens
3. Admin dashboard
4. Register school
5. School detail
6. Class-section list
7. Create class-section modal
8. Students list
9. Enroll student
10. Student detail
11. Student mobile home
12. Student AI tutor
13. Student tasks
14. Student portfolio

This gives the project a complete visual base while matching the backend features that already exist.
