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

export function getFeeds() {
  return fetch("/api/feeds", {
    headers: new Headers({ Accept: "application/json" }),
  });
}
