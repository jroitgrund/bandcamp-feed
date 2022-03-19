import classNames from "classnames";
import { useCallback } from "react";
import { Feed } from "../lib/api";
import Link from "next/link";
import { AppContext } from "../lib/context";
import { buttonClasses, linkClasses } from "../lib/css";

export default function Feeds() {
  return (
    <AppContext.Consumer>
      {(context) => <FeedsImpl feeds={context.feeds} />}
    </AppContext.Consumer>
  );
}

function FeedsImpl(props: { feeds: Array<Feed> }) {
  return (
    <div>
      {props.feeds.length === 0 ? (
        <div className="flex flex-col items-center gap-4">
          <div className="text-3xl">You don&apos;t have any feeds yet</div>
          <div>
            <Link href="/edit-feed" passHref={true}>
              <button className={classNames(buttonClasses)}>
                Create a feed
              </button>
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            <Link href="/edit-feed" passHref={true}>
              <button className={classNames(buttonClasses)}>Create feed</button>
            </Link>
          </div>
          <div className="bg-pink-300 p-2">
            <div className="font-semibold mb-2">Your feeds</div>
            <ul>
              {props.feeds.map((feed) => (
                <FeedItem key={feed.id} feed={feed} />
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

function FeedItem(props: { feed: Feed }) {
  const copyUrl = useCallback(
    () =>
      navigator.clipboard.writeText(
        `${window.location.protocol}//${window.location.host}/feed/${props.feed.id}`
      ),
    [props]
  );
  return (
    <li className="flex justify-between">
      <div>{props.feed.name}</div>
      <div className="flex gap-4">
        <div>
          <Link href={`/edit-feed/${props.feed.id}`}>
            <a className={linkClasses}>Edit</a>
          </Link>
        </div>
        <div>
          <a className={linkClasses} onClick={copyUrl}>
            Copy URL
          </a>
        </div>
      </div>
    </li>
  );
}
