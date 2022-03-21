import classNames, { Argument } from "classnames";
import { omit } from "lodash";

export default function CheckBox(
  props: React.DetailedHTMLProps<
    React.InputHTMLAttributes<HTMLInputElement>,
    HTMLInputElement
  > & { className?: Argument; prefix?: string; label: string }
) {
  return (
    <div className="flex items-center">
      <input
        type="checkbox"
        {...omit(props, "label")}
        className={classNames(
          "focus:ring-0 focus:ring-offset-0 mr-1",
          props.className
        )}
      />
      {props.label}
    </div>
  );
}
