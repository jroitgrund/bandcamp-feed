import { useRouter } from "next/router";
import React, { useState, useEffect, useMemo, useCallback } from "react";
import { FeedWithReleases, getFeed, getFeeds } from "../../lib/api";
import { AppContext } from "../../lib/context";
import BandcampPlayer from "../../components/BandcampPlayer";
import { HeartIcon, HomeIcon } from "@heroicons/react/solid";
import { EyeIcon } from "@heroicons/react/outline";
import Link from "next/link";
import Anchor from "../../components/Anchor";
import InfiniteScroll from "react-infinite-scroll-component";

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
  const { id, startLoading, doneLoading } = props;

  const [feed, setFeed] = useState<FeedWithReleases | undefined>(undefined);
  const releases = useMemo(() => (feed != null ? feed.releases : []), [feed]);

  useEffect(() => {
    if (feed == null) {
      getFeed(id).then((feed) => {
        setFeed(feed);
      });
    }
  }, [id, feed]);

  const nextPage = useCallback(() => {
    getFeed(id, feed?.nextPageKey).then((newFeed) => {
      setTimeout(
        () =>
          setFeed({
            ...newFeed,
            releases: [...releases, ...newFeed.releases],
          })
        // 20000
      );
    });
  }, [id, feed, releases]);

  const items = useMemo(
    () => releases.map((r) => <BandcampPlayer id={r.id} key={r.id} />),
    [releases]
  );

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
      <InfiniteScroll
        dataLength={items.length}
        next={nextPage}
        hasMore={feed.nextPageKey != null}
        loader={
          <div className="flex justify-center mt-8 mb-8">
            <HeartIcon className="h-5 w-5 text-pink-500 animate-ping" />
          </div>
        }
        endMessage={
          <div className="flex justify-center mt-8 mb-8">
            <EyeIcon className="h-5 w-5 text-pink-400" />
          </div>
        }
      >
        <div className="flex flex-col gap-4">{items}</div>
      </InfiniteScroll>
    </>
  );
}
