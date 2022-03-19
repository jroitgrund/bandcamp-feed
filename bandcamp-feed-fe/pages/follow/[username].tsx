import { useRouter } from "next/router";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  BandcampPrefix,
  createFeed,
  Feed,
  getUserPrefixes,
  updateFeed,
} from "../../lib/api";
import { ExternalLinkIcon } from "@heroicons/react/solid";
import { AppContext } from "../../lib/context";
import { filter, includes, map, some, sortBy } from "lodash";
import Anchor from "../../components/Anchor";
import Button from "../../components/Button";
import TextInput from "../../components/TextInput";

export default function Follow() {
  return (
    <AppContext.Consumer>
      {(context) => (
        <FollowImpl feeds={context.feeds} loadFeeds={context.loadFeeds} />
      )}
    </AppContext.Consumer>
  );
}

function FollowImpl(props: { feeds: Feed[]; loadFeeds: () => Promise<void> }) {
  const { feeds, loadFeeds } = props;
  const router = useRouter();
  const [prefixes, setPrefixes] = useState<BandcampPrefix[]>([]);
  const [newFeedName, setNewFeedName] = useState<string>("");
  const [selectedPrefix, setSelectedPrefix] = useState<
    BandcampPrefix | undefined
  >(undefined);
  const username = router.query.username as string | undefined;

  useEffect(() => {
    if (username != null) {
      getUserPrefixes(username).then(setPrefixes);
    }
  }, [username]);

  const addToFeed = useCallback(
    async (prefix: BandcampPrefix, feed: Feed) => {
      setSelectedPrefix(undefined);
      feed.prefixes.push(prefix);
      await updateFeed(feed);
      await loadFeeds();
    },
    [loadFeeds]
  );

  const addToNewFeed = useCallback(
    async (prefix: BandcampPrefix, name: string) => {
      setSelectedPrefix(undefined);
      await createFeed({
        name,
        prefixes: [prefix],
      });
      await loadFeeds();
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

  if (username == null) {
    return null;
  }

  return (
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
                  <Button onClick={() => addToFeed(selectedPrefix, feed)}>
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
            <Button onClick={() => addToNewFeed(selectedPrefix, newFeedName)}>
              add to new feed
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
