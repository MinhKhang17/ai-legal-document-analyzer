# API Convention

## 1. Purpose

This document defines standards for designing and implementing REST APIs.

Goals:

* Consistency
* Readability
* Scalability
* Maintainability

---

# 2. Base URL

All APIs must be versioned.

Format

```text
/api/v1
```

Examples

```http
/api/v1/users
/api/v1/orders
/api/v1/products
```

---

# 3. Resource Naming

Use plural nouns.

Good

```http
/users
/orders
/products
```

Bad

```http
/user
/getUsers
/createUser
/deleteUser
```

---

# 4. URL Convention

Use kebab-case.

Good

```http
/user-profiles
/order-items
/payment-methods
```

Bad

```http
/userProfiles
/user_profiles
```

---

# 5. HTTP Methods

## GET

Retrieve data.

```http
GET /users
GET /users/{id}
```

## POST

Create resource.

```http
POST /users
```

## PUT

Replace resource.

```http
PUT /users/{id}
```

## PATCH

Partial update.

```http
PATCH /users/{id}
```

## DELETE

Delete resource.

```http
DELETE /users/{id}
```

---

# 6. Business Actions

For domain-specific actions.

```http
POST /orders/{id}/approve

POST /orders/{id}/cancel

POST /orders/{id}/complete

POST /users/{id}/reset-password
```

---

# 7. Pagination

Request

```http
GET /users?page=1&size=20
```

Response

```json
{
  "data": [],
  "page": 1,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

# 8. Sorting

```http
GET /users?sort=createdAt,desc
```

Multiple sort

```http
GET /users?sort=name,asc&sort=createdAt,desc
```

---

# 9. Filtering

```http
GET /users?status=ACTIVE

GET /orders?customerId=1

GET /products?category=BOOK
```

---

# 10. Success Response

```json
{
  "success": true,
  "data": {},
  "message": "Success"
}
```

---

# 11. Error Response

```json
{
  "success": false,
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

# 12. Status Code

200 OK

201 Created

204 No Content

400 Bad Request

401 Unauthorized

403 Forbidden

404 Not Found

409 Conflict

422 Unprocessable Entity

500 Internal Server Error

---

# 13. API Documentation

All APIs must be documented using OpenAPI/Swagger.

Requirements:

* Summary
* Description
* Request Example
* Response Example
* Error Example
* Status Codes