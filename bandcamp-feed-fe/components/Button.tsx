import { HeartIcon } from "@heroicons/react/solid";
import classNames, { Argument } from "classnames";
import { omit } from "lodash";

export default function Button(
  props: React.DetailedHTMLProps<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    HTMLButtonElement
  > & { className?: Argument; isLoading?: boolean }
) {
  return (
    <button
      {...omit(props, "isLoading")}
      className={classNames(
        "cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 disabled:border-gray-500 disabled:cursor-not-allowed",
        props.className
      )}
    >
      {props.children}
      &nbsp;
      {props.isLoading ? (
        <HeartIcon className="h-5 w-5 text-pink-500 animate-ping inline-block" />
      ) : null}
    </button>
  );
}
