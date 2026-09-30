type MetricCardProps = {
  label: string;
  value: string | number;
  detail?: string;
  emphasis?: boolean;
};

export default function MetricCard({ label, value, detail, emphasis = false }: MetricCardProps) {
  return (
    <article className={`metric${emphasis ? " metric-emphasis" : ""}`}>
      <span className="metric-label">{label}</span>
      <strong className="metric-value">{value}</strong>
      {detail && <small className="metric-detail">{detail}</small>}
    </article>
  );
}
