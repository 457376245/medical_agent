import Link from "next/link";

type TimelineProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
};

export function TimelineProfileList({ profiles }: { profiles: TimelineProfile[] }) {
  if (profiles.length === 0) {
    return <p className="muted">暂无疾病时间线分组，请先通过右上角“上传”按钮添加病历。</p>;
  }

  return (
    <ul className="timeline-list">
      {profiles.map((profile) => (
        <li className="timeline-item" key={profile.profileId}>
          <div>
            <Link className="timeline-node-link" href={`/profiles/${profile.profileId}`}>
              <strong>{profile.diseaseName}</strong>
            </Link>
            <p className="muted muted-tight">
              最近报告日期：{profile.latestRecordAt ?? "暂无"}
            </p>
            <p className="muted mono muted-tight">
              分组编号：{profile.profileId}
            </p>
          </div>
          <span className="badge">{profile.recordCount} 条记录</span>
        </li>
      ))}
    </ul>
  );
}
