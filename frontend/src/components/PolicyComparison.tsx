import type { PolicyAnalysis, SimulationResult } from "../services/api";

type Props = { analysis: PolicyAnalysis | null; loading: boolean };

export default function PolicyComparison({ analysis, loading }: Props) {
  if (loading && !analysis) {
    return <section className="panel result-panel empty-state"><span className="spinner" />Running the same workload against LRU and LFU…</section>;
  }
  if (!analysis) {
    return (
      <section className="panel result-panel empty-state">
        <span className="empty-mark" aria-hidden="true">↗</span>
        <h2>Results will appear here</h2>
        <p>Choose a simulated healthcare profile and run a measured comparison.</p>
      </section>
    );
  }

  const ordered = ["LRU", "LFU"].map((policy) =>
    analysis.comparisons.find((result) => result.policy === policy),
  ).filter((result): result is SimulationResult => Boolean(result));
  const lru = ordered.find((result) => result.policy === "LRU");
  const lfu = ordered.find((result) => result.policy === "LFU");
  const hitDelta = lru && lfu ? (lfu.stats.hitRate - lru.stats.hitRate) * 100 : 0;
  const characteristics = analysis.characteristics;
  const mostAccessedKeys = characteristics.mostAccessedKeys ?? [];
  const frequencyDistribution = characteristics.frequencyDistribution ?? [];
  const workloadType = characteristics.workloadType ?? (
    characteristics.frequencyConcentration === "High" && characteristics.temporalLocalityLevel === "High"
      ? "Frequency and temporal"
      : characteristics.frequencyConcentration === "High"
        ? "Frequency-heavy"
        : characteristics.temporalLocalityLevel === "High" ? "Temporal-heavy" : "Mixed"
  );
  const commonBuckets = [...frequencyDistribution]
    .sort((a, b) => b.keyCount - a.keyCount || a.accessesPerKey - b.accessesPerKey)
    .slice(0, 6);

  return (
    <section className="analysis-results" aria-live="polite">
      <div className="comparison-grid">
        {ordered.map((result) => (
          <article className="panel policy-result" key={result.policy}>
            <div className="result-heading">
              <span className={`policy-mark ${result.policy.toLowerCase()}`} aria-hidden="true" />
              <div><span className="eyebrow">MEASURED POLICY</span><h3>{result.policy}</h3></div>
            </div>
            <div className="result-rate">{(result.stats.hitRate * 100).toFixed(1)}<small>%</small></div>
            <div className="result-rate-label">Hit rate</div>
            <div className="bar-track" role="progressbar" aria-label={`${result.policy} hit rate`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={result.stats.hitRate * 100}>
              <span style={{ width: `${Math.min(100, result.stats.hitRate * 100)}%` }} />
            </div>
            <dl className="result-stats">
              <div><dt>Miss rate</dt><dd>{(result.stats.missRate * 100).toFixed(1)}%</dd></div>
              <div><dt>Hits</dt><dd>{result.stats.hits.toLocaleString()}</dd></div>
              <div><dt>Misses</dt><dd>{result.stats.misses.toLocaleString()}</dd></div>
              <div><dt>Evictions</dt><dd>{result.stats.evictions.toLocaleString()}</dd></div>
              <div><dt>Requests</dt><dd>{result.stats.totalRequests.toLocaleString()}</dd></div>
            </dl>
          </article>
        ))}
      </div>

      {lru && lfu && <div className="comparison-deltas" aria-label="Measured differences">
        <span><b>{Math.abs(hitDelta).toFixed(1)} pp</b> hit-rate difference{hitDelta === 0 ? "" : ` (${hitDelta > 0 ? "LFU" : "LRU"} higher measured hit rate)`}</span>
        <span><b>{Math.abs(lfu.stats.misses - lru.stats.misses).toLocaleString()}</b> miss difference</span>
        <span><b>{Math.abs(lfu.stats.evictions - lru.stats.evictions).toLocaleString()}</b> eviction difference</span>
      </div>}

      <section className="panel characterization">
        <div className="section-heading">
          <div><span className="eyebrow">WORKLOAD PROFILE</span><h2>Observed workload pattern</h2></div>
          <span className="profile-chip">{analysis.workloadName}</span>
        </div>
        <p className="workload-type">Measured workload type: <strong>{workloadType}</strong></p>
        <div className="character-grid">
          <div className="character-stat"><strong>{analysis.characteristics.uniqueKeys.toLocaleString()}</strong><span>Unique keys</span></div>
          <div className="character-stat"><strong>{analysis.characteristics.repeatedAccesses.toLocaleString()}</strong><span>Repeated accesses</span></div>
          <div className="character-stat"><strong>{(analysis.characteristics.topTenPercentAccessShare * 100).toFixed(1)}%</strong><span>Top 10% access share</span></div>
          <div className="character-stat"><strong>{(analysis.characteristics.temporalLocality * 100).toFixed(1)}%</strong><span>Recent reuse within 50 requests</span></div>
        </div>
        <div className="character-levels">
          <span>Frequency concentration <b className={`level level-${analysis.characteristics.frequencyConcentration.toLowerCase()}`}>{analysis.characteristics.frequencyConcentration}</b></span>
          <span>Temporal locality <b className={`level level-${analysis.characteristics.temporalLocalityLevel.toLowerCase()}`}>{analysis.characteristics.temporalLocalityLevel}</b></span>
        </div>
        <div className="trace-details">
          <div>
            <h3>Most accessed synthetic keys</h3>
            {mostAccessedKeys.length > 0 ? <ol className="top-keys">{mostAccessedKeys.map((item) =>
              <li key={item.key}><code>{item.key}</code><span>{item.accesses.toLocaleString()} accesses · {(item.accessShare * 100).toFixed(1)}%</span></li>,
            )}</ol> : <p className="distribution-note">Detailed synthetic key data is unavailable from this backend response. Restart the backend to load the current analysis fields.</p>}
          </div>
          <div>
            <h3>Key frequency distribution</h3>
            <p className="distribution-note">Most common per-key access counts, from the exact measured distribution.</p>
            <div className="frequency-buckets">{commonBuckets.map((bucket) => <span key={bucket.accessesPerKey}>
              <b>{bucket.keyCount.toLocaleString()}</b> keys × {bucket.accessesPerKey.toLocaleString()} accesses
            </span>)}</div>
            {frequencyDistribution.length > commonBuckets.length &&
              <small className="distribution-note">Showing {commonBuckets.length} of {frequencyDistribution.length} distinct frequency buckets.</small>}
          </div>
        </div>
      </section>

      <section className="insight-panel" aria-label="Observed policy behavior">
        <div className="insight-icon" aria-hidden="true">i</div>
        <div><span className="eyebrow">OBSERVED BEHAVIOR</span><p>{analysis.insight}</p><small>Measured on {analysis.characteristics.requests.toLocaleString()} requests at capacity {analysis.capacity.toLocaleString()}. Results are specific to this simulated trace.</small></div>
      </section>
    </section>
  );
}
