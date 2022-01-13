import classNames from "classnames";
import { concat, filter, omit, sortBy, without } from "lodash";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BandcampPrefix, Feed, NewFeed } from "./Feed";

type AppState =
  | { state: "LOADING" }
  | { state: "NOT_LOGGED_IN" }
  | {
      state: "VIEWING_FEEDS";
      feeds: Array<Feed>;
    }
  | {
      state: "EDITING_FEED";
      feed: Feed | NewFeed;
    };

function App() {
  const [state, setState] = useState<AppState>({ state: "LOADING" });

  const loadFeeds = useCallback(async () => {
    const result = await fetch("/feeds", {
      headers: new Headers({ Accept: "application/json" }),
    });

    if (result.status === 401) {
      setState({
        state: "NOT_LOGGED_IN",
      });
    } else {
      const feeds = await result.json();
      setState({
        state: "VIEWING_FEEDS",
        feeds,
      });
    }
  }, []);

  useEffect(() => {
    loadFeeds();
  }, [loadFeeds]);

  const editFeed = useCallback((feed: Feed) => {
    setState({
      state: "EDITING_FEED",
      feed,
    });
  }, []);

  const createFeed = useCallback(() => {
    setState({
      state: "EDITING_FEED",
      feed: {
        name: "",
        prefixes: [],
      },
    });
  }, []);

  let contents: JSX.Element;
  if (state.state === "LOADING") {
    contents = <Loading />;
  } else if (state.state === "NOT_LOGGED_IN") {
    contents = <LogIn />;
  } else if (state.state === "VIEWING_FEEDS") {
    contents = <Feeds {...state} editFeed={editFeed} createFeed={createFeed} />;
  } else if (state.state === "EDITING_FEED") {
    contents = <EditFeed {...state} loadFeeds={loadFeeds} />;
  } else {
    throw Error();
  }

  return (
    <div className="min-h-screen v-screen bg-pink-200">
      <div className="mx-auto max-w-4xl min-h-screen p-2">
        <div className="border-b-4 border-b-pink-500 flex justify-between mb-10 font-mono">
          <div className="text-xl cursor-default hover:text-pink-500">
            bandcamp-feed
          </div>
          <div className="text-lg">
            <a
              className="cursor-pointer hover:underline decoration-wavy decoration-pink-500"
              href="https://github.com/jroitgrund"
            >
              github
            </a>
            &nbsp;|&nbsp;
            <a
              className="cursor-pointer hover:underline decoration-wavy decoration-pink-500"
              onClick={() => null}
            >
              help
            </a>
          </div>
        </div>
        <div>{contents}</div>
      </div>
    </div>
  );
}

function Loading() {
  return <div></div>;
}

function LogIn() {
  return (
    <div className="flex flex-col items-center">
      <a
        className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 text-3xl"
        href="/login"
      >
        Log in{" "}
      </a>
    </div>
  );
}

function Feeds(props: {
  feeds: Array<Feed>;
  editFeed: (feed: Feed) => void;
  createFeed: () => void;
}) {
  return (
    <div>
      {props.feeds.length === 0 ? (
        <div className="flex flex-col items-center gap-4">
          <div className="text-3xl">You don't have any feeds yet</div>
          <div>
            <button
              onClick={props.createFeed}
              className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500"
            >
              Create a feed
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            <button
              className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500"
              onClick={props.createFeed}
            >
              Create feed
            </button>
          </div>
          <div className="bg-pink-300 p-2">
            <div className="font-semibold mb-2">Your feeds</div>
            <ul>
              {props.feeds.map((feed) => (
                <FeedItem key={feed.id} feed={feed} editFeed={props.editFeed} />
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

function FeedItem(props: { feed: Feed; editFeed: (feed: Feed) => void }) {
  const editFeed = useCallback(() => props.editFeed(props.feed), [props]);
  const copyUrl = useCallback(
    () =>
      navigator.clipboard.writeText(
        `${window.location.protocol}//${window.location.host}/feed/${props.feed.id}`
      ),
    [props]
  );
  return (
    <li className="flex gap-8 justify-between">
      <div>{props.feed.name}</div>
      <div className="flex gap-4">
        <div>
          <a
            className="cursor-pointer hover:underline decoration-wavy decoration-pink-500"
            onClick={editFeed}
          >
            Edit
          </a>
        </div>
        <div>
          <a
            className="cursor-pointer hover:underline decoration-wavy decoration-pink-500"
            onClick={copyUrl}
          >
            Copy URL
          </a>
        </div>
      </div>
    </li>
  );
}

function EditFeed(props: {
  feed: Feed | NewFeed;
  loadFeeds: () => Promise<void>;
}) {
  const [feed, setFeed] = useState(props.feed);
  const [username, setUsername] = useState("");
  const [availablePrefixes, setAvailablePrefixes] = useState<
    Array<BandcampPrefix>
  >([]);

  const allPrefixes = useMemo(
    () => new Set(feed.prefixes.map((prefix) => prefix.bandcampPrefix)),
    [feed.prefixes]
  );
  const newPrefixes = useMemo(
    () =>
      availablePrefixes.filter(
        (prefix) => !allPrefixes.has(prefix.bandcampPrefix)
      ),
    [allPrefixes, availablePrefixes]
  );

  const updateFeedName = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    setFeed((f) => ({
      ...f,
      name: e.target.value,
    }));
  }, []);

  const updateUsername = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    setUsername(e.target.value);
  }, []);

  const loadFromUser = useCallback(async () => {
    setAvailablePrefixes(
      sortBy(
        await (
          await fetch(`/user/${username}`, {
            headers: new Headers({
              Accept: "application/json",
            }),
          })
        ).json(),
        (prefix) => prefix.name
      )
    );
  }, [username]);

  const save = useCallback(async () => {
    if ("id" in feed) {
      await fetch(`/feed/${feed.id}`, {
        headers: new Headers({
          "Content-Type": "application/json",
        }),
        method: "PUT",
        body: JSON.stringify({
          name: feed.name,
          prefixes: feed.prefixes.map((p) => p.bandcampPrefix),
        }),
      });
    } else {
      await fetch("/new-feed", {
        headers: new Headers({
          "Content-Type": "application/json",
        }),
        method: "POST",
        body: JSON.stringify({
          name: feed.name,
          prefixes: feed.prefixes.map((p) => p.bandcampPrefix),
        }),
      });
    }

    props.loadFeeds();
  }, [props, feed]);

  const removePrefix = useCallback((prefix: string) => {
    setFeed((f) => ({
      ...f,
      prefixes: filter(f.prefixes, (p) => p.bandcampPrefix !== prefix),
    }));
  }, []);

  const addPrefix = useCallback((prefix: BandcampPrefix) => {
    setFeed((f) => ({
      ...f,
      prefixes: sortBy(concat(f.prefixes, prefix)),
    }));
  }, []);

  return (
    <div className="flex gap-4 flex-col">
      <div className="flex gap-2">
        <input
          type="text"
          value={feed.name}
          onChange={updateFeedName}
          placeholder="Feed name..."
          className="placeholder-gray-500 bg-pink-200 p-1 underline decoration-pink-300 decoration-4 focus:outline-none focus:decoration-pink-400"
          minLength={1}
        />
        <button
          value="Save"
          className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 disabled:border-gray-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
          onClick={save}
          disabled={feed.name.length === 0 || feed.prefixes.length === 0}
        >
          Save
        </button>
      </div>
      {feed.prefixes.length === 0 && availablePrefixes.length === 0 ? (
        <div className="flex justify-center">
          <div className="flex gap-1">
            <input
              type="text"
              value={username}
              onChange={updateUsername}
              placeholder="Bandcamp username..."
              className="placeholder-gray-500 bg-pink-200 p-1 underline decoration-pink-300 decoration-4 focus:outline-none focus:decoration-pink-400"
            />
            <button
              className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 disabled:border-gray-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
              onClick={loadFromUser}
              disabled={username.length === 0}
            >
              Load from user
            </button>
          </div>
        </div>
      ) : (
        <div className="flex gap-2 items-start">
          <div className="flex-1">
            <ul>
              {feed.prefixes.map((prefix) => (
                <Prefix
                  key={prefix.bandcampPrefix}
                  prefix={prefix}
                  removePrefix={removePrefix}
                />
              ))}
            </ul>
          </div>
          {availablePrefixes.length === 0 ? (
            <div className="flex gap-2">
              <input
                type="text"
                value={username}
                onChange={updateUsername}
                placeholder="Bandcamp username..."
                className="placeholder-gray-500 bg-pink-200 p-1 underline decoration-pink-300 decoration-4 focus:outline-none focus:decoration-pink-400"
              />
              <button
                className="cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 disabled:border-gray-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
                onClick={loadFromUser}
                disabled={username.length === 0}
              >
                Load from user
              </button>
            </div>
          ) : (
            <ul>
              {newPrefixes.map((prefix) => (
                <AvailablePrefix
                  key={prefix.bandcampPrefix}
                  prefix={prefix}
                  addPrefix={addPrefix}
                />
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function Prefix(props: {
  prefix: BandcampPrefix;
  removePrefix: (prefix: string) => void;
}) {
  const removePrefix = useCallback(() => {
    props.removePrefix(props.prefix.bandcampPrefix);
  }, [props]);
  return (
    <li>
      {props.prefix.name} <button onClick={removePrefix}>remove</button>
    </li>
  );
}

function AvailablePrefix(props: {
  prefix: BandcampPrefix;
  addPrefix: (prefix: BandcampPrefix) => void;
}) {
  const addPrefix = useCallback(() => {
    props.addPrefix(props.prefix);
  }, [props]);
  return (
    <li className="flex gap-2">
      {props.prefix.name}
      <button onClick={addPrefix}>add</button>
    </li>
  );
}

export default App;
