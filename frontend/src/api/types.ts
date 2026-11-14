// Shapes from the live OpenAPI spec (https://jfj-s529.onrender.com/v3/api-docs).

export type Mode = "WORKER" | "PROVIDER";
export type PayType = "FIXED" | "HOURLY" | "DAILY";
export type ExperienceLevel = "BEGINNER" | "INTERMEDIATE" | "EXPERIENCED";
export type GigStatus = "DRAFT" | "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export type ApplicationStatus = "APPLIED" | "SHORTLISTED" | "HIRED" | "REJECTED" | "WITHDRAWN";
export type AssignmentStatus = "ACTIVE" | "COMPLETED" | "REMOVED";
export type ReviewDirection = "PROVIDER_TO_WORKER" | "WORKER_TO_PROVIDER";

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface TokenResponse { accessToken: string; tokenType: string; expiresIn: number; mode: Mode }
export interface RatingSummary { average: number | null; count: number }
export interface LevelInfo { level: number; name: string; totalXp: number }
export interface Category { id: number; name: string }
export interface Skill { id: number; name: string }

export interface Me {
  id: string; name: string; email: string; phone: string; role: "USER" | "ADMIN";
  emailVerified: boolean; mode: Mode; workerRating: RatingSummary; providerRating: RatingSummary; level: LevelInfo;
}

export interface RegisterRequest {
  name: string; email: string; phone: string; password: string; dateOfBirth: string; gender?: string;
  addressLine: string; city: string; state: string; pincode: string;
}

export interface WorkerProfile {
  userId: string; name: string; photoUrl: string | null; description: string | null; languages: string[];
  experienceYears: number | null; experienceLevel: ExperienceLevel | null; skills: Skill[];
  profileComplete: boolean; missing: string[];
}

export interface PublicWorkerProfile extends Omit<WorkerProfile, "missing"> {
  city: string; rating: RatingSummary; level: LevelInfo; completedGigs: number;
}

export interface GigSummary {
  id: string; title: string; category: Category; payType: PayType; payAmount: number; workersNeeded: number;
  minExperience: ExperienceLevel | null; startDate: string; durationText: string; locality: string; city: string;
  lat: number; lng: number; distanceKm: number | null; createdAt: string;
}

export interface ExactLocation { lat: number; lng: number; addressLine: string; providerPhone: string }
export interface ProviderSummary { id: string; name: string; rating: RatingSummary; level: LevelInfo }

export interface GigDetail {
  id: string; title: string; description: string; category: Category; payType: PayType; payAmount: number;
  workersNeeded: number; openingsLeft: number; minExperience: ExperienceLevel | null; startDate: string;
  durationText: string; applyDeadline: string | null; skills: Skill[]; locality: string; city: string;
  lat: number; lng: number; status: GigStatus; provider: ProviderSummary; mine: boolean;
  exact: ExactLocation | null; createdAt: string; minLevel: number | null;
}

export interface OwnerGig {
  id: string; title: string; description: string; category: Category; payType: PayType; payAmount: number;
  workersNeeded: number; minExperience: ExperienceLevel | null; startDate: string; durationText: string;
  applyDeadline: string | null; skills: Skill[]; lat: number; lng: number; approxLat: number; approxLng: number;
  addressLine: string; locality: string; city: string; locationEditsLeft: number; status: GigStatus;
  createdAt: string; minLevel: number | null;
}

export interface GigRequest {
  title: string; description: string; categoryId: number; payType: PayType; payAmount: number; workersNeeded: number;
  minExperience?: ExperienceLevel | null; startDate: string; durationText: string; applyDeadline?: string | null;
  skillIds?: number[]; lat: number; lng: number; addressLine: string; locality: string; city: string; minLevel?: number | null;
}

export interface MapPin { id: string; title: string; payAmount: number; payType: PayType; categoryId: number; lat: number; lng: number }
export interface MapResponse { items: MapPin[]; truncated: boolean }

export interface Address {
  label: string; addressLine: string; locality: string; city: string; state: string; pincode: string; lat: number; lng: number;
}

export interface GigBrief {
  id: string; title: string; status: GigStatus; startDate: string; payType: PayType; payAmount: number; locality: string; city: string;
}
export interface AssignmentBrief { id: string; status: AssignmentStatus; removedReason: string | null; endedAt: string | null }

export interface Application {
  id: string; status: ApplicationStatus; note: string | null; createdAt: string; gig: GigBrief; assignment: AssignmentBrief | null;
}

export interface ApplicantWorker {
  id: string; name: string; city: string; photoUrl: string | null; experienceLevel: ExperienceLevel | null;
  experienceYears: number | null; languages: string[]; skills: Skill[]; rating: RatingSummary; level: LevelInfo;
}
export interface Applicant {
  id: string; status: ApplicationStatus; note: string | null; createdAt: string; worker: ApplicantWorker; assignmentId: string | null;
}

export interface Assignment {
  id: string; status: AssignmentStatus; removedReason: string | null; endedAt: string | null; hiredAt: string;
  gig: GigBrief; cancelReason: string | null; reviewedByMe: boolean; reviewOpenUntil: string | null;
}

export interface Contact { role: string; name: string; phone: string; phoneVerified: boolean; availableUntil: string | null }

export interface Review {
  id: string; assignmentId: string; direction: ReviewDirection; rating: number; comment: string | null;
  reviewerId: string; reviewerName: string; editCount: number; createdAt: string; editableUntil: string | null;
  /** not in the API yet; used if the backend starts sending the job with each review */
  gig?: { id: string; title: string } | null;
}

export interface PendingReview { assignmentId: string; workerId: string; workerName: string; reviewOpenUntil: string }
