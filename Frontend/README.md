# MicroInvent Frontend

React, TypeScript, and Vite frontend.

## Run locally

```powershell
npm ci
npm run dev
```

## Current screens

- `/login` — login form
- `/forgot-password` — email, OTP, and new-password flow
- `/catalog/products`
- `/catalog/groups`
- `/catalog/suppliers`
- `/catalog/customers`
- `/receipts` — receipt list, draft creation/editing, details, confirm/cancel
- `/admin/users` — user and warehouse-permission management screens

Catalog, receipt, and user-management screens currently use local sample data.
Changes only update the current browser session; API integration is not connected yet.

## Validation

```powershell
npm run build
npm run lint
```
