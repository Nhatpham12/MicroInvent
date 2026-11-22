# MicroInvent

## Frontend

The React/Vite frontend is in `Frontend/`.

```powershell
cd Frontend
npm ci
npm run dev
```

Authentication screens are available at `/login` and `/forgot-password`. Catalog screens
are available at `/catalog/products`, `/catalog/groups`, `/catalog/suppliers`, and
`/catalog/customers`. Receipt screens are available at `/receipts`; user management is at
`/admin/users`. These follow the Hi-fi scripts and design tokens; catalog, receipt, and
user data are currently local sample data and API calls are not connected yet.
Validate the frontend with `npm run build` and `npm run lint`.
