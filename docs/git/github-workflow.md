# GitHub Workflow & Naming Convention

## 1. Branch Strategy

```text
main
└── develop
    ├── feature/*
    ├── bugfix/*
    ├── hotfix/*
    ├── refactor/*
    └── chore/*
```

---

# 2. Branch Naming

Format

```text
<type>/<ticket>-<description>
```

Examples

```text
feature/CRM-123-create-user-api

feature/CRM-456-order-search

bugfix/CRM-222-fix-login

hotfix/CRM-999-fix-prod-crash

refactor/CRM-321-clean-user-service

chore/CRM-444-upgrade-spring
```

---

# 3. Commit Convention

Use Conventional Commits.

Format

```text
<type>(scope): <description>
```

Examples

```text
feat(user): add create user api

feat(order): implement order search

fix(auth): resolve jwt validation issue

fix(payment): handle timeout exception

refactor(user): simplify mapping logic

docs(api): update swagger documentation

test(order): add unit tests

chore(deps): upgrade spring boot

build(docker): update docker image

ci(github): add deployment workflow
```

---

# 4. Pull Request Title

Format

```text
[TICKET-ID] Description
```

Examples

```text
[CRM-123] Create User API

[CRM-456] Add Order Search API

[CRM-999] Fix Production Login Issue
```

---

# 5. Pull Request Rules

Requirements before merge:

* CI Passed
* Build Passed
* No Conflict
* At Least One Approval
* Documentation Updated

---

# 6. Development Flow

Create Branch

```bash
git checkout develop

git pull origin develop

git checkout -b feature/CRM-123-create-user-api
```

Commit

```bash
git add .

git commit -m "feat(user): add create user api"
```

Push

```bash
git push origin feature/CRM-123-create-user-api
```

Create Pull Request

```text
feature/*
        ↓
Pull Request
        ↓
Code Review
        ↓
Merge Develop
```

---

# 7. Protected Branches

Protected:

```text
main
develop
```

Restrictions

* No direct push
* Pull Request required
* Review required
* CI required

---

# 8. Release Flow

```text
feature/*
      ↓
develop
      ↓
release/*
      ↓
main
      ↓
tag
```

Example

```text
v1.0.0
v1.1.0
v1.2.0
```

---

# 9. Forbidden Practices

Do not:

* Push directly to main
* Force push shared branches
* Merge failed CI
* Commit secrets
* Commit generated files
* Use meaningless commit messages

Bad

```text
update

fix

test

abc
```

Good

```text
feat(user): add user search api

fix(auth): resolve token expiration issue
```