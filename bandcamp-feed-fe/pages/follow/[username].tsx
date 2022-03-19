import { useRouter } from "next/router";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  BandcampPrefix,
  createFeed,
  Feed,
  getUserPrefixes,
  updateFeed,
} from "../../lib/api";
import { ExternalLinkIcon, HomeIcon } from "@heroicons/react/solid";
import { AppContext } from "../../lib/context";
import { filter, includes, map, some, sortBy } from "lodash";
import Anchor from "../../components/Anchor";
import Button from "../../components/Button";
import TextInput from "../../components/TextInput";
import Link from "next/link";

export default function Follow() {
  return (
    <AppContext.Consumer>
      {(context) => (
        <FollowImpl
          feeds={context.feeds}
          loadFeeds={context.loadFeeds}
          startLoading={context.startLoading}
          doneLoading={context.doneLoading}
        />
      )}
    </AppContext.Consumer>
  );
}

const NEW_FEED_LOADING = "NEW_FEED_LOADING";

function FollowImpl(props: {
  feeds: Feed[] | undefined;
  loadFeeds: () => Promise<void>;
  startLoading: () => void;
  doneLoading: () => void;
}) {
  const { feeds, loadFeeds, startLoading, doneLoading } = props;
  const router = useRouter();
  const [prefixes, setPrefixes] = useState<BandcampPrefix[]>([]);
  const [newFeedName, setNewFeedName] = useState<string>("");
  const [selectedPrefix, setSelectedPrefix] = useState<
    BandcampPrefix | undefined
  >(undefined);
  const username = router.query.username as string | undefined;

  const [loading, setLoading] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (feeds == null) {
      startLoading();
      loadFeeds().then(doneLoading);
    }
  }, [feeds, loadFeeds, startLoading, doneLoading]);

  useEffect(() => {
    if (username != null) {
      startLoading();
      getUserPrefixes(username).then(setPrefixes).then(doneLoading);
    }
  }, [username, startLoading, doneLoading]);

  const addToFeed = useCallback(
    async (prefix: BandcampPrefix, feed: Feed) => {
      setLoading(feed.id);

      feed.prefixes.push(prefix);
      await updateFeed(feed);
      await loadFeeds();
      setSelectedPrefix(undefined);
      setLoading(undefined);
    },
    [loadFeeds]
  );

  const addToNewFeed = useCallback(
    async (prefix: BandcampPrefix, name: string) => {
      setLoading(NEW_FEED_LOADING);
      await createFeed({
        name,
        prefixes: [prefix],
      });
      await loadFeeds();
      setSelectedPrefix(undefined);
      setLoading(undefined);
    },
    [loadFeeds]
  );

  const sortedUnusedPrefixes = useMemo(
    () =>
      sortBy(
        filter(
          prefixes,
          (prefix) =>
            !some(feeds, (feed) =>
              includes(
                map(feed.prefixes, (feedPrefix) => feedPrefix.bandcampPrefix),
                prefix.bandcampPrefix
              )
            )
        ),
        (prefix) => prefix.name
      ),
    [prefixes, feeds]
  );

  if (username == null || feeds == null) {
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
      <div className="flex gap-4">
        <ul className="basis-1/3">
          {sortedUnusedPrefixes.map((prefix) => (
            <li key={prefix.bandcampPrefix} className="flex items-center">
              <Anchor
                onClick={() => {
                  setSelectedPrefix(prefix);
                  setNewFeedName("");
                }}
              >
                {prefix.name}
              </Anchor>
              &nbsp;
              <a
                target="_blank"
                rel="noopener noreferrer"
                href={`https://${prefix.bandcampPrefix}.bandcamp.com`}
              >
                <ExternalLinkIcon className="h-5 w-5 hover:text-pink-500" />
              </a>
            </li>
          ))}
        </ul>
        <div className="basis-2/3">
          {selectedPrefix == null ? null : (
            <>
              <div className="font-bold text-pink-500 mb-2">
                {selectedPrefix?.name}
              </div>
              <ul className="flex flex-wrap gap-2 mb-2">
                {feeds.map((feed) => (
                  <li key={feed.id}>
                    <Button
                      disabled={loading != null}
                      isLoading={loading === feed.id}
                      onClick={() => addToFeed(selectedPrefix, feed)}
                    >
                      add to {feed.name}
                    </Button>
                  </li>
                ))}
              </ul>
              <TextInput
                className="mr-2"
                value={newFeedName}
                onChange={(e) => setNewFeedName(e.target.value)}
              />
              <Button
                disabled={loading != null}
                isLoading={loading === NEW_FEED_LOADING}
                onClick={() => addToNewFeed(selectedPrefix, newFeedName)}
              >
                add to new feed
              </Button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
