package com.openspine.mcp;

import com.openspine.OpenSpine;

/** Console launcher that always selects OpenSpine's MCP mode before forwarding user arguments. */
public final class McpLauncher {
	public static void main(String[] args) {
		OpenSpine.main(withMcpMode(args));
	}

	static String[] withMcpMode(String[] args) {
		int length = args == null ? 0 : args.length;
		String[] forwarded = new String[length + 1];
		forwarded[0] = "--mcp";
		if (length > 0) System.arraycopy(args, 0, forwarded, 1, length);
		return forwarded;
	}

	private McpLauncher() {}
}
