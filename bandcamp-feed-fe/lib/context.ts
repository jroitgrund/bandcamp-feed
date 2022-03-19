import React from "react";
import { Feed } from "./api";

export type IAppContext = {
  feeds: Array<Feed>;
  loadFeeds: () => Promise<void>;
};

export const AppContext = React.createContext<IAppContext>({} as IAppContext);
