import React, { useCallback, useEffect, useState } from "react";
import { deleteFeed, Feed } from "../lib/api";
import Link from "next/link";
import { AppContext } from "../lib/context";
import Button from "../components/Button";
import Anchor from "../components/Anchor";
import TextInput from "../components/TextInput";
import {
  ClipboardCopyIcon,
  PencilAltIcon,
  TrashIcon,
} from "@heroicons/react/solid";

export default function Feeds() {
  return (
    <AppContext.Consumer>
      {(context) => (
        <FeedsImpl
          feeds={context.feeds}
          loadFeeds={context.loadFeeds}
          startLoading={context.startLoading}
          doneLoading={context.doneLoading}
        />
      )}
    </AppContext.Consumer>
  );
}

function FeedsImpl(props: {
  feeds: Array<Feed> | undefined;
  startLoading: () => void;
  doneLoading: () => void;
  loadFeeds: () => Promise<void>;
}) {
  const [prefix, setPrefix] = useState("");
  const { feeds, startLoading, doneLoading, loadFeeds } = props;
  useEffect(() => {
    if (feeds == null) {
      startLoading();
      loadFeeds().then(doneLoading);
    }
  }, [feeds, startLoading, doneLoading, loadFeeds]);

  if (feeds == null) {
    return null;
  }

  return (
    <div>
      {feeds.length === 0 ? (
        <div className="flex flex-col items-center gap-4">
          <div className="text-3xl">You don&apos;t have any feeds yet</div>
          <div className="flex gap-2 items-center">
            <TextInput
              prefix="https://bandcamp.com/"
              onChange={(e) => setPrefix(e.target.value)}
              placeholder="username"
            />
            <Link href={`/follow/${prefix}`} passHref={true}>
              <a>
                <Button>Import from Bandcamp</Button>
              </a>
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-col">
          <div className="flex gap-2 mb-4 items-center">
            <TextInput
              prefix="https://bandcamp.com/"
              onChange={(e) => setPrefix(e.target.value)}
              placeholder="username"
            />
            <Link href={`/follow/${prefix}`} passHref={true}>
              <a>
                <Button>Import from Bandcamp</Button>
              </a>
            </Link>
          </div>
          <div className="font-bold text-lg">Your feeds</div>
          <ul>
            {feeds.map((feed) => (
              <FeedItem key={feed.id} feed={feed} />
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function FeedItem(props: { feed: Feed }) {
  return (
    <AppContext.Consumer>
      {(context) => (
        <FeedItemImpl
          {...props}
          startLoading={context.startLoading}
          doneLoading={context.doneLoading}
          loadFeeds={context.loadFeeds}
        />
      )}
    </AppContext.Consumer>
  );
}

function FeedItemImpl(props: {
  feed: Feed;
  startLoading: () => void;
  doneLoading: () => void;
  loadFeeds: () => Promise<void>;
}) {
  const { startLoading, doneLoading, loadFeeds, feed } = props;
  const deleteThisFeed = useCallback(async () => {
    startLoading();
    await deleteFeed(feed.id);
    await loadFeeds();
    doneLoading();
  }, [startLoading, doneLoading, loadFeeds, feed]);
  const copyUrl = useCallback(
    () =>
      navigator.clipboard.writeText(
        `${window.location.protocol}//${window.location.host}/api/feed/${feed.id}`
      ),
    [feed]
  );
  return (
    <li className="flex justify-between">
      <div>
        <Link href={`/feed/${feed.id}`}>
          <a>
            <Anchor>{feed.name}</Anchor>
          </a>
        </Link>
      </div>
      <div className="flex gap-4">
        <div>
          <Link href={`/edit-feed/${feed.id}`} passHref={true}>
            <a>
              <PencilAltIcon className="h-5 w-5 text-pink-400 hover:text-pink-500 inline-block" />
            </a>
          </Link>
        </div>
        <div>
          <a onClick={copyUrl}>
            <Anchor>
              <ClipboardCopyIcon className="h-5 w-5 text-pink-400 hover:text-pink-500 inline-block" />
            </Anchor>
          </a>
        </div>
        <div>
          <a onClick={deleteThisFeed}>
            <Anchor>
              <TrashIcon className="h-5 w-5 text-pink-400 hover:text-pink-500 inline-block" />
            </Anchor>
          </a>
        </div>
      </div>
    </li>
  );
}
