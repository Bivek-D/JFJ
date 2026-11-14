# JFJ frontend

Web app for JFJ, the local gig marketplace: find gigs on a map or list, apply and get hired, or post gigs and hire.

**Stack:** React 19, TypeScript, Vite 6, React Router 7, Leaflet (react-leaflet + marker clustering), Vitest.

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
```

The dev server forwards `/api` and `/actuator` to the live backend (`https://jfj-s529.onrender.com`), so no local backend is needed. On Vercel, `vercel.json` does the same.

The free backend sleeps when idle. The first request can take up to a minute; the app shows a "waking up" note meanwhile.

## Other commands

```bash
npm run build      # typecheck + production build into dist/
npm test           # unit tests (vitest)
npm run typecheck
```

## Settings

Copy `.env.example` to `.env`:

- `VITE_API_BASE`: API path (default `/api/v1`).
- `VITE_MAP_TILE_KEY`: MapTiler key for map tiles in production. Without it the app uses Stadia tiles, which work keyless on localhost only.

## Layout

```
src/
  api/         API client (token refresh, errors), endpoint wrappers, types
  auth/        session: login, refresh on load, worker/provider mode switch
  components/  shell (header, bottom nav), UI pieces, gig card, modals
  map/         gig map, gig area map, location picker
  pages/       Explore, gig detail, My jobs, profile, post/manage gigs…
  lib/         formatting, art, review → job lookup
  styles/      app.css (design tokens and components)
public/art/    category illustrations used on cards and map pins
```
