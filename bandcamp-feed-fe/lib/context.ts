import React from "react";
import { Feed } from "./api";

export type IAppContext = {
  feeds: Array<Feed> | undefined;
  loadFeeds: () => Promise<void>;
  loading: number;
  startLoading: () => void;
  doneLoading: () => void;
};

export const AppContext = React.createContext<IAppContext>({} as IAppContext);
