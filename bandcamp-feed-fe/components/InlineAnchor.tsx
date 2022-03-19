import classNames, { Argument } from "classnames";

export default function InlineAnchor(
  props: React.DetailedHTMLProps<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    HTMLButtonElement
  > & { className?: Argument }
) {
  return (
    <button
      {...props}
      className={classNames(
        "cursor-pointer hover:before:content-['['] hover:after:content-[']'] text-pink-900",
        props.className
      )}
    />
  );
}
