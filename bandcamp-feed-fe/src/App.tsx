import { concat, omit, sortBy, without } from "lodash";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Feed, NewFeed } from "./Feed";

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
    <div className="mx-auto max-w-4xl bg-white h-screen p-1">{contents}</div>
  );
}

function Loading() {
  return <div>"..."</div>;
}

function LogIn() {
  return (
    <div>
      <a href="/login">log in </a>
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
      <div>
        <button onClick={props.createFeed}>Create feed</button>
      </div>
      {props.feeds.length === 0 ? (
        "No feeds yet"
      ) : (
        <ul>
          {props.feeds.map((feed) => (
            <FeedItem key={feed.id} feed={feed} editFeed={props.editFeed} />
          ))}
        </ul>
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
    <li className="flex gap-2">
      <div>
        {props.feed.name} ({props.feed.prefixes.length})
      </div>
      <button onClick={editFeed}>Edit</button>
      <button onClick={copyUrl}>Copy URL</button>
    </li>
  );
}

function EditFeed(props: {
  feed: Feed | NewFeed;
  loadFeeds: () => Promise<void>;
}) {
  const [feed, setFeed] = useState(props.feed);
  const [username, setUsername] = useState("");
  const [availablePrefixes, setAvailablePrefixes] = useState<Array<string>>([]);

  const allPrefixes = useMemo(() => new Set(feed.prefixes), [feed.prefixes]);
  const newPrefixes = useMemo(
    () => availablePrefixes.filter((prefix) => !allPrefixes.has(prefix)),
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
      await (
        await fetch(`/user/${username}`, {
          headers: new Headers({
            Accept: "application/json",
          }),
        })
      ).json()
    );
  }, [username]);

  const save = useCallback(async () => {
    if ("id" in feed) {
      await fetch(`/feed/${feed.id}`, {
        headers: new Headers({
          "Content-Type": "application/json",
        }),
        method: "PUT",
        body: JSON.stringify(omit(feed, "id")),
      });
    } else {
      await fetch("/new-feed", {
        headers: new Headers({
          "Content-Type": "application/json",
        }),
        method: "POST",
        body: JSON.stringify(omit(feed, "id")),
      });
    }

    props.loadFeeds();
  }, [props, feed]);

  const removePrefix = useCallback((prefix: string) => {
    setFeed((f) => ({
      ...f,
      prefixes: without(f.prefixes, prefix),
    }));
  }, []);

  const addPrefix = useCallback((prefix: string) => {
    setFeed((f) => ({
      ...f,
      prefixes: sortBy(concat(f.prefixes, prefix)),
    }));
  }, []);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2">
        <input
          type="text"
          value={feed.name}
          onChange={updateFeedName}
          placeholder="..."
          className="ring-2 rounded-md placeholder-gray-400 p-1"
        />
        <button value="Save" className="rounded-md p-1 ring-2" onClick={save}>
          Save
        </button>
      </div>
      <div className="flex gap-2">
        <input
          type="text"
          value={username}
          onChange={updateUsername}
          placeholder="..."
          className="ring-2 rounded-md placeholder-gray-400 p-1"
        />
        <button className="rounded-md p-1 ring-2" onClick={loadFromUser}>
          Load from user
        </button>
      </div>
      <div className="flex gap-2">
        <ul className="flex-1">
          {feed.prefixes.map((prefix) => (
            <Prefix key={prefix} prefix={prefix} removePrefix={removePrefix} />
          ))}
        </ul>
        <ul className="flex-1">
          {newPrefixes.map((prefix) => (
            <AvailablePrefix
              key={prefix}
              prefix={prefix}
              addPrefix={addPrefix}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}

function Prefix(props: {
  prefix: string;
  removePrefix: (prefix: string) => void;
}) {
  const removePrefix = useCallback(() => {
    props.removePrefix(props.prefix);
  }, [props]);
  return (
    <li>
      {props.prefix} <button onClick={removePrefix}>remove</button>
    </li>
  );
}

function AvailablePrefix(props: {
  prefix: string;
  addPrefix: (prefix: string) => void;
}) {
  const addPrefix = useCallback(() => {
    props.addPrefix(props.prefix);
  }, [props]);
  return (
    <li className="flex gap-2">
      {props.prefix}
      <button onClick={addPrefix}>add</button>
    </li>
  );
}

export default App;
