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
import Checkbox from "../../components/Checkbox";
import TextInput from "../../components/TextInput";
import Button from "../../components/Button";

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
  const { id } = props;

  const [feed, setFeed] = useState<FeedWithReleases | undefined>(undefined);
  const [includePrereleases, setIncludePreleases] = useState(true);
  const [fromDate, setFromDate] = useState<string | undefined>(undefined);
  const [fromDateText, setFromDateText] = useState("");
  const [loading, setLoading] = useState(false);
  const releases = useMemo(() => (feed != null ? feed.releases : []), [feed]);

  useEffect(() => {
    setLoading(true);
    getFeed(id, includePrereleases, fromDate).then((feed) => {
      setFeed(feed);
      setLoading(false);
    });
  }, [id, includePrereleases, fromDate]);

  const nextPage = useCallback(() => {
    getFeed(id, includePrereleases, fromDate, feed?.nextPageKey).then(
      (newFeed) => {
        setFeed({
          ...newFeed,
          releases: [...releases, ...newFeed.releases],
        });
      }
    );
  }, [id, feed, releases, includePrereleases, fromDate]);

  const items = useMemo(
    () => releases.map((r) => <BandcampPlayer id={r.id} key={r.id} />),
    [releases]
  );

  return (
    <>
      <div className="flex gap-4 items-center mb-2">
        <div className="flex-1">
          <Link href="/" passHref={true}>
            <a>
              <div className="flex items-center gap-1 leading-5">
                <HomeIcon className="h-5 w-5 text-pink-400" />
                <Anchor href="/">Back to feeds</Anchor>
              </div>
            </a>
          </Link>
        </div>
        <Checkbox
          label="include preleases"
          onChange={(e) => setIncludePreleases(e.target.checked)}
          checked={includePrereleases}
        />
        <div className="flex items-center gap-2">
          <TextInput
            type="text"
            value={fromDateText}
            onChange={(e) => setFromDateText(e.target.value)}
            placeholder="dd/mm/yyyy"
            pattern="\d\d/\d\d/\d\d\d\d"
          />
          <Button
            onClick={() => {
              const [_, day, month, year] = fromDateText.match(
                /(\d\d)\/(\d\d)\/(\d\d\d\d)/
              )!;
              setFromDate(`${year}-${month}-${day}`);
            }}
            disabled={loading}
          >
            Start from date
          </Button>
        </div>
      </div>
      {feed != null ? (
        <div className="mb-2 font-bold text-lg">{feed.name}</div>
      ) : null}
      {loading ? (
        <div className="mt-20 flex justify-center">
          <HeartIcon className="h-5 w-5 text-pink-500 animate-pulse" />
        </div>
      ) : null}
      {feed != null && !loading ? (
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
      ) : null}
    </>
  );
}
