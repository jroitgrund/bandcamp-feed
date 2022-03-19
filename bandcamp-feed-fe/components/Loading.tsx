import classNames from "classnames";
import styles from "./Loading.module.css";

export default function Loading() {
  return (
    <div className="flex flex-col align items-center">
      <div className={classNames(styles["lds-heart"], "flex-1")}>
        <div></div>
      </div>
    </div>
  );
}
