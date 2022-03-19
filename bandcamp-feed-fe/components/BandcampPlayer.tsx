export default function BandcampPlayer(props: { id: string }) {
  return (
    <iframe
      src={`https://bandcamp.com/EmbeddedPlayer/v=2/album=${props.id}/size=large/tracklist=true/artwork=small/`}
      style={{ height: "50vh" }}
    />
  );
}
