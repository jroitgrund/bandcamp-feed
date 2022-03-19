import classNames, { Argument } from "classnames";

export default function TextInput(
  props: React.DetailedHTMLProps<
    React.InputHTMLAttributes<HTMLInputElement>,
    HTMLInputElement
  > & { className?: Argument }
) {
  return (
    <input
      type="text"
      {...props}
      className={classNames(
        "placeholder-gray-500 bg-pink-200 p-1 border-pink-300 border-4 focus:outline-none focus:border-pink-400",
        props.className
      )}
    />
  );
}
