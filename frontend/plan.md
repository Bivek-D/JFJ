JFJ - Frontend Phase-wise Plan (React + Vite)
Companion to the JFJ PRD v2.0 and the Backend Phase-wise Plan. The stack below is a proposal and needs the frontend developer's confirmation. Time estimates are theirs to set; this plan fixes order, scope and exit criteria.
Stack and conventions
• Stack: React with TypeScript, Vite, React Router, TanStack Query, Tailwind CSS, React Hook Form with Zod, react-leaflet with Leaflet.markercluster, MSW for mocks, Vitest and Testing Library, Playwright for end-to-end.
• Structure: src/api (generated client), src/app (providers, routes), src/components (shared UI), src/features/<feature> (screens, hooks, components per feature), src/lib (auth, geo, formatting), src/styles.
• API client: typed client generated from the backend's OpenAPI spec. Base URL comes from an environment variable and points to /api/v1, which the frontend host rewrites to the backend so the refresh cookie stays first-party.
• Auth: access token held in memory only (never localStorage or sessionStorage). The refresh token is an HttpOnly cookie. On load, call refresh to restore the session. On a 401, run one shared refresh attempt (single flight) and retry the request once; if it fails, go to login. The refresh response carries the user's last active mode, so a reload keeps the same mode.
• Forms: Zod schemas mirror backend validation. Backend fieldErrors are mapped onto form fields. Dates (start date, deadline) are entered and shown in Asia/Kolkata, the backend's business timezone.
• Every screen has four states: loading (skeleton), empty, error (with retry) and success. Cold start handling: if the first request takes more than 3 seconds, show a 'server is waking up' banner instead of a blank screen.
• Responsive: mobile-first from 360 px wide. Android Chrome is the priority browser.
• Privacy in the client: never put coordinates, tokens or personal details in URLs, analytics or console logs.
• Definition of done for every phase: works against both the mock server and the hosted backend; usable at 360 px; keyboard reachable with visible focus; no console errors; unit or integration tests for non-trivial logic; pull request with screenshots; CI (lint, typecheck, tests, build) green; deployed as a preview.
F0 - Setup
Starts: immediately.
Tasks
1. Create the Vite TypeScript project, ESLint, Prettier, path aliases, environment handling (VITE_API_BASE_URL, map tile key, with a committed .env.example).
2. Tailwind setup with design tokens: colors, spacing, type scale, radius, light theme first. Define the shared components: Button, Input, Select, Textarea, Checkbox, Card, Badge, Avatar, Modal, Drawer, Toast, Skeleton, EmptyState, ErrorState, Spinner, Tabs, Pagination.
3. App shell: router, layout with header and mobile navigation, error boundary, 404 and 403 pages.
4. API layer: generate the typed client from the OpenAPI spec, wrap it with the auth interceptor, and standardize error handling (backend error body to a user-facing message, 429 with retry hint).
5. MSW mock handlers for the endpoints of the next phases, driven by the spec and a shared seed set so the frontend can work before the backend is live.
6. Cold-start detector: ping the health endpoint on app start and show the waking-up banner when slow.
7. Hosting: Vercel (or Netlify or Cloudflare Pages) project with a rewrite from /api/* to the backend, preview deployments per pull request, security headers including a Content Security Policy that allows only the tile, Cloudinary and own-origin sources you use.
8. CI: lint, typecheck, unit tests and build on every pull request.
Exit criterion: an empty app deploys, loads through the rewrite and shows the backend health status.
F1 - Authentication and account
Starts when: backend phase B1 is live on the hosted instance (mocks until then).
Screens
• Register: name, email, phone, password, DOB (client-side 18+ check), address fields, optional home location pin (opens the picker from F3 lazily). Show backend field errors inline.
• Verify: 6-digit code entry, resend with a countdown, clear messages for expired code and for the attempt limit.
• Login, Forgot password, Reset password (generic confirmation text so accounts cannot be enumerated).
• Mode switch: header toggle that calls switch-mode, swaps the in-memory token and redirects to that mode's home.
• Settings: edit account fields, update home location, delete account with a typed confirmation.
• Help: static content.
Behavior
• Route guards by login state and by mode; admin routes guarded by role.
• Session restore on page load; logout clears memory and calls the backend.
• A suspended account (403) lands on a dedicated screen with the reason to contact support, not a generic error.
• 429 responses show the wait time.
Tests: the single-flight refresh under parallel 401s; guard redirects; form validation; OTP resend countdown.
Exit criterion: a user can register, verify, log in, switch mode and stay signed in across a page refresh; delete account works.
F2 - Worker profile
Starts when: backend phase B2 is live.
Screens and components
• Profile edit: photo upload, description, languages, experience level and years, optional gender and the medical-notes opt-in (kept as the PRD specifies).
• Skill picker: searchable catalog list with a free-text option.
• Work proofs: add and delete entries with title, description and up to 3 photos each.
• Public worker profile: the limited view providers see, with rating, level badge and completed gigs.
• Shared components: RatingStars, LevelBadge, ProfileCompletionBar.
Behavior
• Image upload: compress in the browser to a sensible size, request signed parameters from the backend, upload straight to Cloudinary, then save the returned URL. Show progress and handle failure and retry.
• A banner and a disabled Apply button explain what is missing until the profile is complete.
Tests: upload flow with a mocked Cloudinary response; skill picker; completion banner logic.
Exit criterion: a worker can complete a profile with photos and skills, and the Apply gate follows completeness.
F3 - Provider posting and the location picker
Starts when: backend phase B3 is live (geo endpoints included).
LocationPicker component (also used by registration and settings)
1. Loads lazily so the map bundle stays out of the main chunk.
2. Opens at browser geolocation. Fallback order: saved user location, registered city, default India view. Handles permission denied gracefully.
3. Draggable marker plus a search box with autocomplete against the backend geo proxy, debounced about 300 ms, cancelling stale requests. Choosing a suggestion moves the marker.
4. On marker drop, reverse geocode and prefill locality, city and address line, all editable.
5. An explicit 'Confirm location' button is required before the value is accepted.
6. Empty or poor autocomplete results are expected in small towns; the pin remains the primary input and the UI says so.
7. Map tiles from the configured provider with the required attribution.
Screens
• Post gig (stepped form): basics (title, category, description), pay (type and amount, workers needed), schedule (start date, duration, optional deadline), location (picker), requirements (skills, minimum experience and level), photos (up to 5, same upload flow as F2), review and publish.
• Drafts: save as draft at any step; publish calls the publish endpoint.
• Edit gig: fields disabled when the backend rules forbid editing (hires exist); show the reason. Location edits show the remaining edit count.
• My gigs: list with status tabs (Draft, Open, In progress, Completed, Cancelled, Expired) and actions per status.
Tests: picker state machine (permission denied, no results, confirm required); form step validation; publish flow against mocks.
Exit criterion: a provider can pin a location, publish a gig and see it in My gigs and in the backend.
F4 - Worker discovery: list and map
Starts when: backend phase B3 is live.
Screens and components
• Gig feed: cards with title, pay, category, approximate distance (rounded as the backend returns it), locality and openings left. Pagination or infinite scroll.
• Filters: drawer or sheet with radius, category, pay range, experience, pay type, start date and keyword; active filters shown as removable chips; filter state held in the URL query string, but never coordinates.
• Gig detail: description, photos, provider name with rating and level, openings left, approximate location on a small map labelled as approximate, and an Apply call to action whose state reflects the situation (log in required, profile incomplete, own gig, no openings, already applied).
• List/map toggle on the feed with shared filters.
• Map view: opens at geolocation, else the saved location; if neither exists, asks the user to search or pin a place and never shows an empty world map. Fetches pins on map move end with a debounce of about 400 ms and aborts stale requests. Clusters pins. Tapping a pin shows a mini card that links to the detail page. Shows a 'zoom in' banner when the backend returns truncated. States clearly that pins are approximate.
Performance behavior
• Default to list view when the connection looks slow or data saver is on; the map bundle loads only when the user opens the map.
• Skeletons while loading; cold-start banner as in F0.
Tests: filter-to-query mapping; map fetch debounce and abort; truncated banner; apply-button state matrix.
Exit criterion: a worker sees nearby gigs as pins, opens one from a pin and from the list, and filters work in both views.
F5 - Applications, hiring and gig management
Starts when: backend phase B4 is live.
Worker screens
• Apply: modal with an optional note; handles 'no openings' and 'profile incomplete' responses.
• MyApplications: tabs by status, withdraw action, status badges, link to the gig.
• Hired gig view: exact address with an exact-position map and the provider contact, visible only after hire. The assignment id comes from GET /assignments/mine (and the application response); the contact and review screens use it. A removed worker sees the removal reason here.
Provider screens (Manage gig page)
• Applicants tab: applicant cards with rating, level, skills, past work and note; sort by rating or level; filters; shortlist, reject and hire actions with optimistic updates and proper handling when the last slot was just filled (backend conflict response).
• Workers tab: active workers with contact, remove action with a required reason, and a hire-replacement prompt.
• Gig actions: start, complete and cancel with confirmation dialogs; cancel requires a reason.
• Contact view: name and phone with an 'unverified' badge and safety tips shown before the first reveal; call link.
Tests: hire conflict handling; remove-then-replace flow; confirmation dialogs; contact view access states (expired window).
Exit criterion: two accounts can run apply, hire, start and complete on the hosted instance entirely through the UI.
F6 - Reviews, levels and notifications
Starts when: backend phase B5 is live.
Screens and components
• Review form: star rating and optional comment, for providers rating workers and workers rating the provider; shows the 24-hour edit countdown and the 14-day review window; reviews on removed assignments are supported.
• Pending reviews: list of workers still to rate for a provider.
• Reviews received: list on profiles.
• XP and level: progress bar toward the next level, level names, recent XP events if the backend exposes them.
• Notification bell: unread count, polling every 30 seconds while the tab is visible (pause when hidden, back off on errors), dropdown and a full notifications page, mark one read, mark all read, deep links by notification type.
• Provider stats: removed count shown on provider profiles.
Tests: polling pause and resume on tab visibility; review edit window states; deep-link mapping for each notification type.
Exit criterion: completing a gig shows XP and level changes and review prompts to both sides, and notifications arrive without a manual refresh.
F7 - Reports and admin console
Starts when: backend phase B6 is live.
Screens
• Report dialog on user profiles and gig pages with reason and note; duplicate-report message handled.
• Admin console (role-guarded): reports queue with resolve and dismiss, user suspend and unsuspend with confirmation, gig removal, category and skill management, level threshold editor, flagged providers list.
Tests: role guard; destructive-action confirmations; report dedupe response.
Exit criterion: an admin can resolve a report and suspend a user from the UI, and a suspended user sees the suspended screen.
F8 - Polish, performance and QA
Starts when: all backend phases are live.
Tasks
1. Performance: route-level code splitting, map in its own lazy chunk, Cloudinary transformations for format and size, a bundle-size budget in CI, Lighthouse mobile checks on the feed, gig detail and post-gig screens.
2. Low-bandwidth behavior: skeletons, image lazy loading, retry on flaky requests, offline banner.
3. Accessibility: labels and error association on forms, focus management in modals and the stepper, contrast checks, a text list alternative for every map view.
4. Compatibility: test the last two versions of Chrome, Edge, Firefox and Safari, plus Android Chrome on a real low-end device if available.
5. Edge states: 404, 403, 500, expired session, suspended account, rate-limited, empty feed, location denied.
6. Security: no tokens in web storage, a strict Content Security Policy, external links with safe attributes, no sensitive data in console output.
7. End-to-end tests (Playwright) on the hosted instance with seed accounts: worker flow (register to apply), provider flow (post to complete), review flow, admin flow, all at a phone-sized viewport.
8. Fix the mismatch log from the weekly integration checks.
Exit criterion: both happy paths pass end to end on a phone-sized viewport on the hosted instance, and the performance budgets hold.
Working with the backend
• Build against MSW mocks until the matching backend phase is on the hosted instance; switch a feature to the real API only when its handoff is confirmed.
• API changes after a contract freeze arrive as pull requests that update the OpenAPI spec and list what breaks. Regenerate the client and fix types before merging.
• Use the shared seed accounts and gigs for development and for the end-to-end tests.
• Once a week, run the current happy path end to end on the hosted instance with the backend developer and log mismatches.
Decisions to confirm
1. Final stack choice (React and Vite proposed) and the UI approach: own Tailwind components or a component library.
2. Tile provider (MapTiler or Stadia) and who owns the account and key.
3. Whether the feed uses pagination or infinite scroll.
4. Whether the map or the list is the default view on desktop.