const LABELS = {
  IN_WORK: 'In Work',
  UNDER_REVIEW: 'Under Review',
  APPROVED: 'Approved',
  RELEASED: 'Released',
  PENDING: 'Pending',
  REJECTED: 'Rejected',
};

export default function StateBadge({ state }) {
  if (!state) return null;
  return <span className={`badge ${state}`}>{LABELS[state] || state}</span>;
}
