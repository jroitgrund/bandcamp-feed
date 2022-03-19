import classNames, { Argument } from "classnames";

export default function Button(
  props: React.DetailedHTMLProps<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    HTMLButtonElement
  > & { className?: Argument }
) {
  return (
    <button
      {...props}
      className={classNames(
        "cursor-pointer border-2 rounded border-pink-400 p-2 bg-pink-300 hover:border-pink-500 disabled:border-gray-500 disabled:bg-gray-100 disabled:cursor-not-allowed",
        props.className
      )}
    />
  );
}
