import { filter, find } from "lodash";
import { useRouter } from "next/router";
import React, { useState, useCallback, ChangeEvent, useEffect } from "react";
import { BandcampPrefix, Feed, updateFeed } from "../../lib/api";
import { AppContext } from "../../lib/context";
import Link from "next/link";
import Button from "../../components/Button";
import TextInput from "../../components/TextInput";
import { TrashIcon } from "@heroicons/react/solid";

export default function EditFeed() {
  const router = useRouter();
  if (router.query["id"] == null) {
    return null;
  }
  const id = router.query["id"] as string;
  return (
    <AppContext.Consumer>
      {(context) => {
        return (
          <EditFeedWrapper
            id={id}
            feeds={context.feeds}
            loadFeeds={context.loadFeeds}
            startLoading={context.startLoading}
            doneLoading={context.doneLoading}
          />
        );
      }}
    </AppContext.Consumer>
  );
}

function EditFeedWrapper(props: {
  id: string;
  feeds: Feed[] | undefined;
  loadFeeds: () => Promise<void>;
  startLoading: () => void;
  doneLoading: () => void;
}) {
  const { id, feeds, loadFeeds, startLoading, doneLoading } = props;
  useEffect(() => {
    if (feeds == null) {
      startLoading();
      loadFeeds().then(doneLoading);
    }
  }, [feeds, startLoading, loadFeeds, doneLoading]);

  if (feeds == null) {
    return null;
  }

  const feed = find(feeds, (feed) => feed.id === id);
  if (feed == null) {
    return null;
  }

  return <EditFeedImpl feed={feed} feeds={feeds} loadFeeds={loadFeeds} />;
}

function EditFeedImpl(props: {
  feed: Feed;
  feeds: Feed[];
  loadFeeds: () => Promise<void>;
}) {
  const { loadFeeds } = props;

  const router = useRouter();
  const [feed, setFeed] = useState(props.feed);

  const updateFeedName = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    setFeed((f) => ({
      ...f,
      name: e.target.value,
    }));
  }, []);

  const save = useCallback(async () => {
    await updateFeed(feed);
    await loadFeeds();
    router.push("/");
  }, [loadFeeds, feed, router]);

  const removePrefix = useCallback((prefix: string) => {
    setFeed((f) => ({
      ...f,
      prefixes: filter(f.prefixes, (p) => p.bandcampPrefix !== prefix),
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
          <a>
            <Button>Cancel</Button>
          </a>
        </Link>
      </div>
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
    <li className="flex items-center">
      {props.prefix.name}
      <TrashIcon
        onClick={removePrefix}
        className="cursor-pointer h-5 w-5 hover:text-pink-500 inline-block"
      />
    </li>
  );
}
