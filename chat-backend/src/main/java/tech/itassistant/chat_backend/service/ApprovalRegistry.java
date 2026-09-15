package tech.itassistant.chat_backend.service;

import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Approvals for the sensitive tool calls of one chat session.
 *
 * <p>Two things are kept apart on purpose: the {@code pending} futures let the agent thread wait
 * for the user, and the {@code approved} set is the record that {@link tech.itassistant.chat_backend.service.mcp.McpToolGateway}
 * checks immediately before it executes a call. An approval is single-use, so the same decision
 * cannot be replayed for a second call.
 */
@Log4j2
public class ApprovalRegistry {

    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();
    private final Set<String> approved = ConcurrentHashMap.newKeySet();

    /**
     * Blocks the agent run until the user decides. A timeout counts as a denial, so a forgotten
     * browser tab cannot keep a tool call waiting forever.
     */
    public boolean awaitDecision(String toolCallId, Duration timeout) {
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        pending.put(toolCallId, decision);
        try {
            return decision.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("awaitDecision() : No decision for tool call {} within {}", toolCallId, timeout);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.error("awaitDecision() : ERROR : {}", e.getMessage(), e);
            return false;
        } finally {
            pending.remove(toolCallId);
        }
    }

    /**
     * Records the user's decision.
     *
     * @return false when no tool call is waiting for this id, which the controller reports as 404
     */
    public boolean decide(String toolCallId, boolean userApproved) {
        CompletableFuture<Boolean> decision = pending.get(toolCallId);
        if (decision == null) {
            return false;
        }
        // The record has to be in place before the waiting thread is released.
        if (userApproved) {
            approved.add(toolCallId);
        }

        boolean released = decision.complete(userApproved);
        if (!released && userApproved) {
            // The call had already timed out, so the approval must not be left lying around.
            approved.remove(toolCallId);
        }
        return released;
    }

    /** Takes the approval for this exact tool call, if there is one. */
    public boolean consumeApproval(String toolCallId) {
        return approved.remove(toolCallId);
    }
}
