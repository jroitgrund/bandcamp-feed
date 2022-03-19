import { sortBy, filter, concat, find } from "lodash";
import { useRouter } from "next/router";
import React, { useState, useMemo, useCallback, ChangeEvent } from "react";
import {
  BandcampPrefix,
  createFeed,
  Feed,
  getUserPrefixes,
  NewFeed,
  updateFeed,
} from "../../lib/api";
import { AppContext } from "../../lib/context";
import Link from "next/link";
import Button from "../../components/Button";
import TextInput from "../../components/TextInput";
import InlineAnchor from "../../components/InlineAnchor";

export default function EditFeed() {
  const router = useRouter();
  if (!router.isReady) {
    return null;
  }
  const id: string | undefined = router.query["id"]?.[0];
  return (
    <AppContext.Consumer>
      {(context) => {
        if (id != null) {
          const feed = find(context.feeds, (feed) => feed.id === id);
          if (feed == null) {
            return null;
          }

          return <EditFeedImpl feed={feed} loadFeeds={context.loadFeeds} />;
        }
        return (
          <EditFeedImpl
            feed={{
              name: "",
              prefixes: [],
            }}
            loadFeeds={context.loadFeeds}
          />
        );
      }}
    </AppContext.Consumer>
  );
}

function EditFeedImpl(props: {
  feed: Feed | NewFeed;
  loadFeeds: () => Promise<void>;
}) {
  const { loadFeeds } = props;
  const router = useRouter();
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
      sortBy(await getUserPrefixes(username), (prefix) => prefix.name)
    );
  }, [username]);

  const save = useCallback(async () => {
    if ("id" in feed) {
      await updateFeed(feed);
    } else {
      await createFeed(feed);
    }

    await loadFeeds();
    router.push("/");
  }, [loadFeeds, feed, router]);

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
    <div className="flex gap-8 flex-col">
      <div className="flex gap-2">
        <TextInput
          type="text"
          value={feed.name}
          onChange={updateFeedName}
          placeholder="Feed name..."
          minLength={1}
        />
        <Button
          value="Save"
          onClick={save}
          disabled={feed.name.length === 0 || feed.prefixes.length === 0}
        >
          Save
        </Button>
        <Link href="/" passHref={true}>
          <Button>Cancel</Button>
        </Link>
      </div>
      {feed.prefixes.length === 0 && availablePrefixes.length === 0 ? (
        <div className="flex justify-center mt-5">
          <div className="flex gap-1">
            <TextInput
              type="text"
              value={username}
              onChange={updateUsername}
              placeholder="Bandcamp username..."
            />
            <Button onClick={loadFromUser} disabled={username.length === 0}>
              Load from user
            </Button>
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
              <TextInput
                type="text"
                value={username}
                onChange={updateUsername}
                placeholder="Bandcamp username..."
              />
              <Button onClick={loadFromUser} disabled={username.length === 0}>
                Load from user
              </Button>
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
      {props.prefix.name}{" "}
      <InlineAnchor onClick={removePrefix}>remove</InlineAnchor>
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
      <InlineAnchor onClick={addPrefix}>add</InlineAnchor>
    </li>
  );
}
