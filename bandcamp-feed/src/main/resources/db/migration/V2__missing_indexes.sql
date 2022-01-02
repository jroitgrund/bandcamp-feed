create table releases_prefixes_v2 (
    release_id text not null,
    bandcamp_prefix text not null,
    primary key (bandcamp_prefix, release_id),
    foreign key (bandcamp_prefix) references bandcamp_prefixes(bandcamp_prefix),
    foreign key (release_id) references releases(release_id)
);

insert into releases_prefixes_v2 select * from releases_prefixes;

drop table releases_prefixes;