import { Component, lazy, Suspense, useEffect, type ReactNode } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import { FullScreenLoader, RequireAuth, Shell } from "./components/Shell";
import { pingHealth } from "./api/client";
import { Empty } from "./components/ui";
import { Link } from "react-router-dom";

const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const Explore = lazy(() => import("./pages/Explore"));
const GigDetailPage = lazy(() => import("./pages/GigDetail"));
const MyJobs = lazy(() => import("./pages/MyJobs"));
const Profile = lazy(() => import("./pages/Profile"));
const WorkerProfileEdit = lazy(() => import("./pages/WorkerProfileEdit"));
const PublicWorker = lazy(() => import("./pages/PublicWorker"));
const PostGig = lazy(() => import("./pages/PostGig"));
const MyGigs = lazy(() => import("./pages/MyGigs"));
const ManageGig = lazy(() => import("./pages/ManageGig"));

function ScrollTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="container narrow">
        <Empty icon="info" title="Something went wrong" action={<button className="btn btn-primary" onClick={() => location.reload()}>Reload</button>}>
          Reload the page to try again.
        </Empty>
      </div>
    );
  }
}

function NotFound() {
  return (
    <Shell>
      <div className="container narrow">
        <Empty icon="search" title="We couldn't find that page" action={<Link className="btn btn-primary" to="/">Go to Explore</Link>}>
          The link may be old, or the gig was removed.
        </Empty>
      </div>
    </Shell>
  );
}

export default function App() {
  // Wake the free backend as early as possible.
  useEffect(() => {
    pingHealth();
  }, []);

  return (
    <ErrorBoundary>
    <Suspense fallback={<FullScreenLoader />}>
      <ScrollTop />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<RequireAuth><Explore /></RequireAuth>} />
        <Route path="/gig/:id" element={<RequireAuth><GigDetailPage /></RequireAuth>} />
        <Route path="/jobs" element={<RequireAuth mode="WORKER"><MyJobs /></RequireAuth>} />
        <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="/profile/edit" element={<RequireAuth><WorkerProfileEdit /></RequireAuth>} />
        <Route path="/workers/:id" element={<RequireAuth><PublicWorker /></RequireAuth>} />
        <Route path="/post" element={<RequireAuth mode="PROVIDER"><PostGig /></RequireAuth>} />
        <Route path="/gigs" element={<RequireAuth mode="PROVIDER"><MyGigs /></RequireAuth>} />
        <Route path="/gigs/:id" element={<RequireAuth mode="PROVIDER"><ManageGig /></RequireAuth>} />
        <Route path="/gigs/:id/edit" element={<RequireAuth mode="PROVIDER"><PostGig /></RequireAuth>} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
    </ErrorBoundary>
  );
}
