import * as ep from "../api/endpoints";
import type { Review } from "../api/types";

export interface ReviewJob { id: string; title: string }

/**
 * Reviews only carry an assignmentId. For the signed-in user's OWN reviews we can still
 * name the job: workers via /assignments/mine, providers via their gigs' applicants
 * (each hire has an assignmentId). Returns assignmentId → gig.
 */
export async function myReviewJobs(direction: Review["direction"]): Promise<Map<string, ReviewJob>> {
  const map = new Map<string, ReviewJob>();
  if (direction === "PROVIDER_TO_WORKER") {
    // reviews about me as a worker → my assignments
    for (let page = 0; page < 10; page++) {
      const d = await ep.myAssignments(page);
      d.items.forEach((a) => map.set(a.id, { id: a.gig.id, title: a.gig.title }));
      if (page + 1 >= d.totalPages) break;
    }
    return map;
  }
  // reviews about me as a provider → hires on gigs that got past "open"
  const gigs = [];
  for (let page = 0; page < 5; page++) {
    const d = await ep.myGigs(undefined, page, 50);
    gigs.push(...d.items.filter((g) => g.status !== "OPEN" && g.status !== "DRAFT"));
    if (page + 1 >= d.totalPages) break;
  }
  await Promise.all(
    gigs.map(async (g) => {
      const list = await ep.gigApplicants(g.id).catch(() => []);
      list.forEach((a) => a.assignmentId && map.set(a.assignmentId, { id: g.id, title: g.title }));
    }),
  );
  return map;
}

/** The job a review is about: the API's own field if the backend ever sends it, else our lookup. */
export function jobFor(r: Review, lookup?: Map<string, ReviewJob> | null): ReviewJob | null {
  return r.gig ?? lookup?.get(r.assignmentId) ?? null;
}
