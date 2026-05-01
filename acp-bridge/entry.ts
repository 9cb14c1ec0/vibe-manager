// Entry point for the ACP bridge standalone binary.
// This imports and runs the claude-agent-acp stdio server,
// which bridges ACP JSON-RPC on stdin/stdout to Claude Code.
import { runAcp } from "@agentclientprotocol/claude-agent-acp";

// stdout is used to send messages to the client
// we redirect everything else to stderr to make sure it doesn't interfere with ACP
console.log = console.error;
console.info = console.error;
console.warn = console.error;
console.debug = console.error;

process.on("unhandledRejection", (reason: unknown, promise: Promise<unknown>) => {
    console.error("Unhandled Rejection at:", promise, "reason:", reason);
});

const { connection, agent } = runAcp();

async function shutdown() {
    await agent.dispose().catch((err: unknown) => {
        console.error("Error during cleanup:", err);
    });
    process.exit(0);
}

// Exit cleanly when the ACP connection closes
connection.closed.then(shutdown);
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

// Keep process alive while connection is open
process.stdin.resume();
