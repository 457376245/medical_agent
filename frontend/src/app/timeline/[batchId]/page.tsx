import { redirect } from "next/navigation";

export default function LegacyTimelineBatchPage({ params }: { params: { batchId: string } }) {
  const batchId = encodeURIComponent(params.batchId);
  redirect(`/timeline?batchId=${batchId}`);
}

