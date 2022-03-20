export interface BandcampPrefix {
  bandcampPrefix: string;
  name: string;
}

export interface Feed {
  name: string;
  id: string;
  prefixes: Array<BandcampPrefix>;
}

export interface NewFeed {
  name: string;
  prefixes: Array<BandcampPrefix>;
}

export interface FeedWithReleases {
  name: string;
  releases: Release[];
  nextPageKey?: string;
}

export interface Release {
  id: string;
  url: string;
  title: string;
  artist: string;
  prefix: string;
  date: string;
}

export function getFeeds() {
  return fetch("/api/feeds", {
    headers: new Headers({ Accept: "application/json" }),
  });
}

export async function getUserPrefixes(
  username: string
): Promise<BandcampPrefix[]> {
  return (
    await fetch(`/api/user/${username}`, {
      headers: new Headers({
        Accept: "application/json",
      }),
    })
  ).json();
}

export function updateFeed(feed: Feed) {
  return fetch(`/api/feed/${feed.id}`, {
    headers: new Headers({
      "Content-Type": "application/json",
    }),
    method: "PUT",
    body: JSON.stringify({
      name: feed.name,
      prefixes: feed.prefixes.map((p) => p.bandcampPrefix),
    }),
  });
}

export function createFeed(feed: NewFeed) {
  return fetch("/api/new-feed", {
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

export async function deleteFeed(feedId: string) {
  return fetch(`/api/feed/${feedId}`, {
    method: "DELETE",
  });
}

export async function getFeed(
  feedId: string,
  nextPageKey?: string
): Promise<FeedWithReleases> {
  return (
    await fetch(
      `/api/feed/${feedId}${nextPageKey != null ? `?page=${nextPageKey}` : ""}`,
      {
        headers: new Headers({ Accept: "application/json" }),
      }
    )
  ).json();
}
