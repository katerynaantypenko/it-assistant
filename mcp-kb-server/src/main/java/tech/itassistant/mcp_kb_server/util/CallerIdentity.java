package tech.itassistant.mcp_kb_server.util;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

/**
 * The signed-in user behind an MCP call. Taken from the validated ID token, never from the
 * tool arguments - otherwise the model could decide who "performed" an action.
 */
public record CallerIdentity(String subject, String email, String name) {

    /** Key under which the caller is stored in the MCP transport context. */
    public static final String CONTEXT_KEY = "caller";

    private static final String UNKNOWN = "unknown";
    private static final CallerIdentity ANONYMOUS = new CallerIdentity(UNKNOWN, UNKNOWN, UNKNOWN);

    /**
     * Reads the caller from the Spring Security context. The MCP transport calls this while the
     * servlet thread is still inside the security filter chain, so the validated JWT is available.
     */
    public static CallerIdentity fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return ANONYMOUS;
        }
        Jwt token = jwtAuthentication.getToken();
        return new CallerIdentity(
                orUnknown(token.getSubject()),
                orUnknown(token.getClaimAsString("email")),
                orUnknown(token.getClaimAsString("name")));
    }

    /**
     * Reads the caller back inside a tool handler, which may run on a different thread than the
     * HTTP request - that is exactly why the identity travels in the transport context.
     */
    public static CallerIdentity from(McpTransportContext context) {
        Object caller = context == null ? null : context.get(CONTEXT_KEY);
        return caller instanceof CallerIdentity identity ? identity : ANONYMOUS;
    }

    public McpTransportContext toTransportContext() {
        return McpTransportContext.create(Map.of(CONTEXT_KEY, this));
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
