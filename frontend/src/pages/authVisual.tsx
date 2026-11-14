export function AuthVisual() {
  return (
    <div className="auth-visual" aria-hidden>
      {[
        { left: "18%", top: "22%", art: "plumbing" },
        { left: "46%", top: "36%", art: "painting", sel: true },
        { left: "72%", top: "24%", art: "cleaning" },
        { left: "62%", top: "52%", art: "gardening" },
      ].map((p) => (
        <div key={p.art} className={`jfj-pin ${p.sel ? "selected" : ""}`} style={{ position: "absolute", left: p.left, top: p.top }}>
          <div className="ring"><img src={`/art/${p.art}.png`} alt="" /></div>
          <div className="tail" />
        </div>
      ))}
      <div className="auth-tagline">
        <div className="h1" style={{ marginBottom: 8 }}>Local work, close to home.</div>
        <p className="body2">Find gigs on the map, apply in a tap, and get hired by people near you.</p>
      </div>
    </div>
  );
}
