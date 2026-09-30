type PolicySelectorProps = {
  value: string;
  onChange: (policy: string) => void;
};

export default function PolicySelector({ value, onChange }: PolicySelectorProps) {
  return (
    <label className="field">
      Eviction policy
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="LRU">LRU</option>
        <option value="LFU">LFU</option>
      </select>
    </label>
  );
}
