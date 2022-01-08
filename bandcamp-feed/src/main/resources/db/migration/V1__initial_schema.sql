create table users (
    user_email text not null primary key
);

create table feeds (
    feed_id text not null primary key,
    feed_name text not null,
    user_email text not null,
    foreign key (user_email) references users(user_email) on delete cascade
);

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
    primary key (bandcamp_prefix, release_id),
    foreign key (release_id) references releases(release_id) on delete cascade,
    foreign key (
        bandcamp_prefix
    ) references bandcamp_prefixes(bandcamp_prefix) on delete cascade
);

create table feeds_prefixes (
    feed_id text not null,
    bandcamp_prefix text not null,
    primary key (feed_id, bandcamp_prefix),
    foreign key (
        bandcamp_prefix
    ) references bandcamp_prefixes(bandcamp_prefix) on delete cascade,
    foreign key (feed_id) references feeds(feed_id) on delete cascade
);
