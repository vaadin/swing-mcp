package com.vaadin.swingmcp.tinymcpserver;

/**
 * Stateless utility methods used by TinyMCPServer.
 */
class ServerUtils {

    private ServerUtils() {}

    static int levenshteinDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) d[i][0] = i;
        for (int j = 0; j <= n; j++) d[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[m][n];
    }
}
