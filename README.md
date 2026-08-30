# LoadShed Planner

A day planner for a Mirpur print shop that schedules jobs around rotating power cuts — auto-placing jobs on a timeline, routing power-hungry jobs to the generator only when unavoidable, and letting the shop owner drag jobs to reschedule with the generator cost updating live.

- **Team ID:** `[FILL IN — e.g. T035]`
- **Problem ID:** `[FILL IN — e.g. P01]`
- **Live URL:** `[FILL IN — not yet deployed; see "Status" below]`

## Status

This repo currently ships:
- Full source code and test suite (Spring Boot backend + vanilla HTML/CSS/JS frontend).
- A `Dockerfile` and a GitHub Actions workflow (`.github/workflows/docker-publish.yml`) that builds and publishes a Docker image to GitHub Container Registry (`ghcr.io/<owner>/loadshed-planner`) on every push.

**It is not yet deployed to a public URL.** The GitHub Action publishes a container image; it does not host it anywhere. Update the "Live URL" field above once that's set up.

## Setup & run

Requires JDK 17+ and Maven (or use the included `mvnw` if present).

```bash
# Run the test suite
mvn test

# Run locally (http://localhost:8080)
mvn spring-boot:run
```

### Run via Docker

```bash
docker build -t loadshed-planner .
docker run -p 8080:8080 loadshed-planner
```

Then open `http://localhost:8080`.

## What it does

- Configure shop hours (supports same-day, overnight, and full 24-hour days).
- Add power cuts (start/end time).
- Add jobs (name, duration in minutes, power requirement: grid-only / generator-capable / no power needed).
- The scheduler auto-places every job on the timeline, preferring grid power and only paying generator minutes when a job can't avoid a cut.
- Drag any placed job left/right to reschedule it — the move is validated server-side and the generator-minutes/cost totals update immediately.
- Jobs that don't fit anywhere are listed under "Could not fit" with a reason.

## Major decisions

- **15-minute internal slot grid.** The scheduler places jobs on a fixed 15-minute slot grid (`Slotizer`) rather than doing continuous-time math, which keeps placement/overlap logic simple array arithmetic. Job durations that aren't a multiple of 15 are rounded **up** to the next slot so they never get truncated/short-changed on the timeline.
- **Best-fit + reinsertion local search**, not a full solver. Jobs are placed group-by-group (grid-constrained jobs first, then free jobs, then generator jobs), each using best-fit placement, followed by a bounded local-search pass that re-seats generator jobs into cheaper slots freed up by later placements. This is a heuristic, not an optimal scheduler — documented in `SchedulerService`'s class-level Javadoc.
- **Drag-to-reschedule moves one job, not a full re-solve.** `SchedulerService.move()` validates and repositions exactly the dragged job against the plan the client already has, so a drag never silently reshuffles other jobs the user didn't touch.
- **Overnight / full-24h shop hours.** Shop hours are modeled with `LocalTime`, which has no native way to represent a 24-hour span. A day "wraps" (and clock-times before open are interpreted as landing on the next calendar day) whenever `close` isn't after `open` — including `open == close`, which is the convention used for a full 24-hour day.
- **Stateless backend, client-held plan.** The server has no database; the current day's plan lives in the browser and is resent on every `/api/schedule` call. This keeps the backend trivial but means the plan doesn't survive a page reload.

## Known limitations

- **No persistence.** Refreshing the page loses the current jobs/cuts/plan — there's no database or session storage.
- **No authentication / single shared plan.** There's no concept of separate users or shops; anyone hitting the app edits the same in-memory plan for their own session only (nothing is shared between browser tabs/users, but nothing is saved either).
- **Generator-cost granularity.** Even though job duration input is now free-form (any number of minutes), generator cost is still computed in whole 15-minute slots internally, so a job's *cost* impact rounds to the nearest slot even when its displayed duration is exact.
- **Not deployed publicly yet.** See "Status" above — only a Docker image is published; no hosting is wired up.
- **No input validation on cut/job overlap sanity** (e.g. a cut with `end` before `start` on an ordinary, non-wrapping day is simply ignored rather than rejected with an error).

## Contributions

| Member | Contribution |
|---|---|
| `[FILL IN]` | `[FILL IN]` |
| `[FILL IN]` | `[FILL IN]` |
| `[FILL IN]` | `[FILL IN]` |

*(Git history shows commits from Rabbi Islam Emon and shamiulriyad — fill in each registered member's actual major contribution here.)*
