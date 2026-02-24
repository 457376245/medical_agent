import { redirect } from "next/navigation";

export default function LegacyTimelineProfilePage({ params }: { params: { profileId: string } }) {
  const profileId = encodeURIComponent(params.profileId);
  redirect(`/timeline?profileId=${profileId}`);
}

