create table feeds (feed_id text not null primary key, feed_name text not null);

create table bandcamp_prefixes (bandcamp_prefix text not null primary key);

create table releases (
    release_id text not null primary key,
    url text not null,
    title text not null,
    artist text not null,
    release_date text not null
);

create table releases_prefixes (
    release_id text not null,
    bandcamp_prefix text not null,
    primary key (release_id, bandcamp_prefix),
    foreign key (bandcamp_prefix) references bandcamp_prefixes(bandcamp_prefix),
    foreign key (release_id) references releases(release_id)
);

create table feeds_prefixes (
    feed_id text not null,
    bandcamp_prefix text not null,
    primary key (feed_id, bandcamp_prefix),
    foreign key (bandcamp_prefix) references bandcamp_prefixes(bandcamp_prefix),
    foreign key (feed_id) references feeds(feed_id)
);
