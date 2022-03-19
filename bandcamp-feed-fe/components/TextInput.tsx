import classNames, { Argument } from "classnames";

export default function TextInput(
  props: React.DetailedHTMLProps<
    React.InputHTMLAttributes<HTMLInputElement>,
    HTMLInputElement
  > & { className?: Argument; prefix?: string }
) {
  return props.prefix == null ? (
    <input
      type="text"
      {...props}
      className={classNames(
        "placeholder-gray-500 bg-pink-200 p-1 border-pink-300 border-4 focus:outline-none focus:border-pink-400",
        props.className
      )}
    />
  ) : (
    <div
      className={classNames(
        "bg-pink-200 p-1 border-pink-300 border-4",
        props.className
      )}
    >
      {props.prefix}
      <input
        type="text"
        className="bg-pink-200 placeholder-gray-500 focus:outline-none"
        {...props}
      />
    </div>
  );
}
