import { api, qs } from "./client";
import type {
  Address, Applicant, Application, ApplicationStatus, Assignment, Category, Contact, GigDetail, GigRequest,
  GigStatus, GigSummary, MapResponse, Me, Mode, OwnerGig, Page, PendingReview, PublicWorkerProfile,
  RegisterRequest, Review, Skill, TokenResponse, WorkerProfile, ExperienceLevel, PayType,
} from "./types";

/* auth */
export const login = (email: string, password: string) =>
  api<TokenResponse>("/auth/login", { body: { email: email.trim().toLowerCase(), password }, publicCall: true });
export const register = (body: RegisterRequest) => api<unknown>("/auth/register", { body, publicCall: true });
export const logout = () => api<void>("/auth/logout", { method: "POST", publicCall: true });
export const switchMode = (mode: Mode) => api<TokenResponse>("/auth/switch-mode", { body: { mode } });
export const getMe = () => api<Me>("/users/me");

/* catalog */
export const getCategories = () => api<Category[]>("/categories");
export const getSkills = () => api<Skill[]>("/skills");

/* worker profile */
export const getMyWorkerProfile = () => api<WorkerProfile>("/workers/me/profile");
export const saveWorkerProfile = (body: {
  photoUrl: string | null; description: string | null; languages: string[]; experienceYears: number;
  experienceLevel: ExperienceLevel;
}) => api<WorkerProfile>("/workers/me/profile", { method: "PUT", body });
export const saveWorkerSkills = (skillIds: number[]) =>
  api<WorkerProfile>("/workers/me/skills", { method: "PUT", body: { skillIds } });
export const getPublicWorker = (userId: string) => api<PublicWorkerProfile>(`/workers/${userId}`);

/* search */
export interface GigFilters {
  q?: string; categoryId?: number | ""; minPay?: number | ""; maxPay?: number | ""; payType?: PayType | "";
  experience?: ExperienceLevel | ""; startFrom?: string;
}
export const searchGigs = (
  f: GigFilters & { lat?: number; lng?: number; radiusKm?: number; page?: number; size?: number },
  signal?: AbortSignal,
) =>
  api<Page<GigSummary>>(
    "/gigs" +
      qs({
        lat: f.lat != null ? f.lat.toFixed(6) : undefined,
        lng: f.lng != null ? f.lng.toFixed(6) : undefined,
        radiusKm: f.lat != null ? f.radiusKm : undefined,
        categoryId: f.categoryId, minPay: f.minPay, maxPay: f.maxPay, payType: f.payType,
        experience: f.experience, startFrom: f.startFrom, q: f.q?.trim(), page: f.page ?? 0, size: f.size ?? 20,
      }),
    { signal },
  );
export const mapGigs = (
  b: { minLat: number; minLng: number; maxLat: number; maxLng: number },
  f: GigFilters,
  signal?: AbortSignal,
) =>
  api<MapResponse>(
    "/gigs/map" +
      qs({
        minLat: b.minLat.toFixed(6), minLng: b.minLng.toFixed(6), maxLat: b.maxLat.toFixed(6), maxLng: b.maxLng.toFixed(6),
        categoryId: f.categoryId, minPay: f.minPay, maxPay: f.maxPay, payType: f.payType, experience: f.experience,
        startFrom: f.startFrom, q: f.q?.trim(),
      }),
    { signal },
  );
export const getGig = (id: string) => api<GigDetail>(`/gigs/${id}`);

/* apply */
export const applyToGig = (gigId: string, note: string) =>
  api<Application>(`/gigs/${gigId}/applications`, { body: note.trim() ? { note: note.trim() } : {} });
export const myApplications = (page = 0) => api<Page<Application>>(`/applications/mine${qs({ page, size: 50 })}`);
export const withdrawApplication = (id: string) => api<void>(`/applications/${id}`, { method: "DELETE" });

/* jobs + contact + reviews */
export const myAssignments = (page = 0) => api<Page<Assignment>>(`/assignments/mine${qs({ page, size: 50 })}`);
export const getContact = (assignmentId: string) => api<Contact>(`/assignments/${assignmentId}/contact`);
export const postReview = (assignmentId: string, rating: number, comment: string) =>
  api<Review>(`/assignments/${assignmentId}/reviews`, { body: { rating, comment: comment.trim() || undefined } });
export const editReview = (id: string, rating: number, comment: string) =>
  api<Review>(`/reviews/${id}`, { method: "PUT", body: { rating, comment: comment.trim() || undefined } });
export const userReviews = (userId: string, page = 0) =>
  api<Page<Review>>(`/users/${userId}/reviews${qs({ page, size: 20 })}`);

/* provider gigs */
export const createGig = (body: GigRequest) => api<OwnerGig>("/gigs", { body });
export const updateGig = (id: string, body: GigRequest) => api<OwnerGig>(`/gigs/${id}`, { method: "PUT", body });
export const publishGig = (id: string) => api<OwnerGig>(`/gigs/${id}/publish`, { method: "POST" });
export const deleteGig = (id: string) => api<void>(`/gigs/${id}`, { method: "DELETE" });
export const myGigs = (status?: GigStatus, page = 0, size = 20) =>
  api<Page<OwnerGig>>(`/gigs/mine${qs({ status, page, size })}`);
export const startGig = (id: string) => api<OwnerGig>(`/gigs/${id}/start`, { method: "POST" });
export const completeGig = (id: string) => api<OwnerGig>(`/gigs/${id}/complete`, { method: "POST" });
export const cancelGig = (id: string, reason: string) => api<OwnerGig>(`/gigs/${id}/cancel`, { body: { reason } });

/* hiring */
export const gigApplicants = (gigId: string, status?: ApplicationStatus, sort?: "NEWEST" | "RATING" | "LEVEL") =>
  api<Applicant[]>(`/gigs/${gigId}/applications${qs({ status, sort })}`);
export const shortlist = (id: string) => api<Applicant>(`/applications/${id}/shortlist`, { method: "POST" });
export const reject = (id: string) => api<Applicant>(`/applications/${id}/reject`, { method: "POST" });
export const hire = (id: string) => api<Applicant>(`/applications/${id}/hire`, { method: "POST" });
export const pendingReviews = (gigId: string) => api<PendingReview[]>(`/gigs/${gigId}/reviews/pending`);

/* geo */
export const geoAutocomplete = (q: string, signal?: AbortSignal) =>
  api<Address[]>(`/geo/autocomplete${qs({ q })}`, { signal });
export const geoReverse = (lat: number, lng: number, signal?: AbortSignal) =>
  api<Address>(`/geo/reverse${qs({ lat: lat.toFixed(6), lng: lng.toFixed(6) })}`, { signal });
