create table feeds (
    feed_id text not null,
    feed_name text not null,
    user_email text not null,
    primary key (feed_id, feed_name, user_email)
);
create index feeds_user_email on feeds(user_email, feed_name, feed_id);

create table bandcamp_prefixes (
    bandcamp_prefix text not null,
    name text not null,
    primary key (bandcamp_prefix, name)
);

create table releases (
    release_id text not null,
    url text not null,
    title text not null,
    artist text not null,
    release_date text not null,
    primary key (release_id, url, title, artist, release_date)
);
create index releases_release_date_release_id
on releases(release_date, release_id);

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
create index feed_prefixes_bandcamp_prefix on feeds_prefixes(bandcamp_prefix);
