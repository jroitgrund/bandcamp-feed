import classNames, { Argument } from "classnames";

export default function Anchor(
  props: React.DetailedHTMLProps<
    React.AnchorHTMLAttributes<HTMLAnchorElement>,
    HTMLAnchorElement
  > & { className?: Argument }
) {
  return (
    <span
      {...props}
      className={classNames(
        "cursor-pointer hover:underline decoration-wavy decoration-pink-500",
        props.className
      )}
    />
  );
}
