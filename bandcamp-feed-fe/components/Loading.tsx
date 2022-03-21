import { HeartIcon } from "@heroicons/react/solid";
import React from "react";

export default function Loading() {
  return (
    <div className="flex flex-col align items-center">
      <HeartIcon className="h-20 w-20 text-pink-500 animate-bounce" />
    </div>
  );
}
