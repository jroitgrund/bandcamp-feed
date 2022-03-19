import classNames from "classnames";
import { buttonClasses } from "../lib/css";

export default function Login() {
  return (
    <div className="flex flex-col items-center">
      <a className={classNames(buttonClasses, "text-3xl")} href="/api/login">
        Log in
      </a>
    </div>
  );
}
