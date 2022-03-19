import Button from "../components/Button";

export default function Login() {
  return (
    <div className="flex flex-col items-center">
      {/*eslint-disable-next-line @next/next/no-html-link-for-pages*/}
      <a href="/api/login">
        <Button className="text-3xl">Log in</Button>
      </a>
    </div>
  );
}
