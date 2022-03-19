import "../styles/globals.css";
import type { AppProps } from "next/app";
import { useCallback, useContext, useEffect, useState } from "react";
import { AppContext, IAppContext } from "../lib/context";
import { getFeeds } from "../lib/api";
import { useRouter } from "next/router";
import { linkClasses } from "../lib/css";

function AppState({ Component, pageProps }: AppProps) {
  const router = useRouter();
  const [state, setState] = useState<IAppContext>({
    feeds: [],
    loadFeeds: async () => {
      const result = await getFeeds();

      if (result.status === 401) {
        router.replace("/login");
      } else {
        const feeds = await result.json();
        setState({
          ...state,
          feeds,
        });
      }
    },
  });

  const loadFeeds = state.loadFeeds;

  useEffect(() => {
    loadFeeds();
  }, [loadFeeds]);

  return (
    <AppContext.Provider value={state}>
      <div className="min-h-screen v-screen bg-pink-200">
        <div className="mx-auto max-w-4xl min-h-screen p-2">
          <div className="border-b-4 border-b-pink-500 flex justify-between mb-10 font-mono">
            <div className="text-xl cursor-default hover:text-pink-500">
              bandcamp-feed
            </div>
            <div className="text-lg">
              <a className={linkClasses} href="https://github.com/jroitgrund">
                github
              </a>
              &nbsp;|&nbsp;
              <a className={linkClasses} onClick={() => null}>
                help
              </a>
            </div>
          </div>
          <div>
            <Component {...pageProps} />
          </div>
        </div>
      </div>
    </AppContext.Provider>
  );
}

export default AppState;
