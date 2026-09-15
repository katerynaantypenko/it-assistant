package tech.itassistant.chat_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.itassistant.chat_backend.dto.ApprovalRequest;
import tech.itassistant.chat_backend.dto.ChatMessageRequest;
import tech.itassistant.chat_backend.service.AccessTokenProvider;
import tech.itassistant.chat_backend.service.AgentService;
import tech.itassistant.chat_backend.service.ChatEventSender;
import tech.itassistant.chat_backend.service.ChatSession;
import tech.itassistant.chat_backend.service.ChatSessionRegistry;

import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/chat")
@Log4j2
@RequiredArgsConstructor
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    private final ChatSessionRegistry chatSessionRegistry;
    private final AgentService agentService;
    private final AccessTokenProvider accessTokenProvider;
    private final ExecutorService agentExecutor;

    /**
     * Starts one agent run and streams it back as server-sent events.
     * The run happens on a worker thread, so the approval call for the same run can be served.
     */
    @PostMapping("/messages")
    public SseEmitter sendMessage(@Valid @RequestBody ChatMessageRequest request,
                                  @AuthenticationPrincipal OidcUser user,
                                  OAuth2AuthenticationToken authentication,
                                  HttpSession httpSession,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        log.info("POST : /api/chat/messages : REQUEST : user={} : {} characters",
                user.getEmail(), request.message().length());

        ChatSession session = chatSessionRegistry.get(httpSession.getId(), user.getEmail());
        // Resolved before the run is handed over, because renewing the token needs this request.
        String accessToken = accessTokenProvider.tokenFor(authentication, httpRequest, httpResponse);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ChatEventSender events = new ChatEventSender(emitter);

        agentExecutor.execute(() -> {
            try {
                agentService.run(session, accessToken, request.message(), events);
            } catch (Exception e) {
                log.error("POST : /api/chat/messages : ERROR : {}", e.getMessage(), e);
                events.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                events.done();
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * The user's decision about a pending tool call. Everything the decision protects is enforced in
     * the tool gateway; this endpoint only records the answer and releases the waiting run.
     */
    @PostMapping("/approvals/{toolCallId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable String toolCallId,
                       @Valid @RequestBody ApprovalRequest request,
                       @AuthenticationPrincipal OidcUser user,
                       HttpSession httpSession) {
        log.info("POST : /api/chat/approvals/{} : REQUEST : user={} : approved={}",
                toolCallId, user.getEmail(), request.approved());

        ChatSession session = chatSessionRegistry.get(httpSession.getId(), user.getEmail());
        if (!session.getApprovals().decide(toolCallId, request.approved())) {
            // Nothing is waiting: the run timed out, was already decided, or the id is wrong.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tool call is waiting for this decision.");
        }
    }
}
