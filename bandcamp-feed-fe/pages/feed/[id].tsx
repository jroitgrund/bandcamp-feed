import { useRouter } from "next/router";
import React, { useState, useEffect } from "react";
import { FeedWithReleases, getFeed } from "../../lib/api";
import { AppContext } from "../../lib/context";
import BandcampPlayer from "../../components/BandcampPlayer";
import { HomeIcon } from "@heroicons/react/solid";
import Link from "next/link";
import Anchor from "../../components/Anchor";

export default function FeedPage() {
  const router = useRouter();
  if (router.query["id"] == null) {
    return null;
  }
  const id = router.query["id"] as string;
  return (
    <AppContext.Consumer>
      {(context) => {
        return (
          <FeedPageImpl
            id={id}
            startLoading={context.startLoading}
            doneLoading={context.doneLoading}
          />
        );
      }}
    </AppContext.Consumer>
  );
}

function FeedPageImpl(props: {
  id: string;
  startLoading: () => void;
  doneLoading: () => void;
}) {
  const [feed, setFeed] = useState<FeedWithReleases | undefined>(undefined);
  const { id, startLoading, doneLoading } = props;

  useEffect(() => {
    if (feed == null) {
      startLoading();
      getFeed(id).then((feed) => {
        setFeed(feed);
        doneLoading();
      });
    }
  }, [id, feed, startLoading, doneLoading]);

  if (feed == null) {
    return null;
  }

  return (
    <>
      <Link href="/" passHref={true}>
        <a>
          <div className="mb-2 flex items-center gap-1 leading-5">
            <HomeIcon className="h-5 w-5 text-pink-400" />
            <Anchor href="/">Back to feeds</Anchor>
          </div>
        </a>
      </Link>
      <div className="mb-2 font-bold text-lg">{feed.name}</div>
      <div className="flex flex-col gap-4">
        {feed.releases.map((r) => (
          <BandcampPlayer id={r.id} key={r.id} />
        ))}
      </div>
    </>
  );
}
