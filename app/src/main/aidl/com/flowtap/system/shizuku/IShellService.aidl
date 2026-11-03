package com.flowtap.system.shizuku;

/** Runs as the shell user inside Shizuku. */
interface IShellService {
    /** Shizuku calls this transaction id to tear the service down. */
    void destroy() = 16777114;

    /** Runs [command] via `sh -c`. Returns "<exitCode>\n<combined output>". */
    String exec(String command) = 1;
}
