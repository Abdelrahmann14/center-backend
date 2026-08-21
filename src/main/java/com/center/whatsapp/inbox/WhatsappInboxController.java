package com.center.whatsapp.inbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.auth.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * «الرسائل» - the conversations a workspace is having with real people.
 *
 * <p>Behind the same {@code NOTIFICATION_SEND} permission as the send buttons,
 * and for the same reason: writing to a parent from the centre's number is the
 * same act of authority whether it is one message or a hundred.
 *
 * <p>Note what is missing: there is no endpoint that starts a conversation with
 * arbitrary text. WhatsApp does not permit it, so neither does this. Free text
 * is a REPLY - {@link #send} works only inside the window a person opened by
 * writing first - and anything else has to go out as an approved template
 * through the existing send paths.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp/inbox")
@PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Messages")
public class WhatsappInboxController {

    /** Enough threads for a long scroll; the list is searched, not paged. */
    private static final int MAX_CONVERSATIONS = 200;

    /** Enough of a thread to scroll back through a term's worth of talking. */
    private static final int MAX_MESSAGES = 500;

    private final WhatsappInboxService inbox;

    @GetMapping("/conversations")
    @Operation(summary = "The workspace's WhatsApp threads, most recent first")
    public List<WhatsappInboxService.Conversation> conversations(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "100") int limit) {
        return inbox.conversations(query, archived, Math.min(Math.max(limit, 1),
                MAX_CONVERSATIONS));
    }

    @GetMapping("/conversations/{id}")
    public WhatsappInboxService.Conversation conversation(@PathVariable UUID id) {
        return inbox.conversation(id);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "One thread, oldest message first")
    public List<WhatsappInboxService.Message> messages(@PathVariable UUID id,
            @RequestParam(defaultValue = "200") int limit) {
        return inbox.messages(id, Math.min(Math.max(limit, 1), MAX_MESSAGES));
    }

    /**
     * Sends one free-form reply.
     *
     * <p>The 24-hour window is re-checked server-side even though the composer
     * is disabled when it is closed: a thread left open on a screen goes stale,
     * and a rejection from Meta is a worse way to find out.
     */
    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Reply inside the 24-hour customer service window")
    public WhatsappInboxService.Message send(@PathVariable UUID id,
            @Valid @RequestBody SendRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return inbox.send(id, request.body(), me == null ? null : me.getUsername());
    }

    public record SendRequest(@NotBlank @Size(max = 4096) String body) {}

    /** Opens the thread for a number that has not written recently. */
    @PostMapping("/conversations")
    @Operation(summary = "Open (or find) the thread for a phone number")
    public WhatsappInboxService.Conversation open(@Valid @RequestBody OpenRequest request) {
        return inbox.open(request.phone());
    }

    public record OpenRequest(@NotBlank String phone) {}

    /** Clears the unread count, and puts the blue ticks on their side. */
    @PostMapping("/conversations/{id}/read")
    public Map<String, Boolean> read(@PathVariable UUID id) {
        inbox.markRead(id);
        return Map.of("ok", true);
    }

    @PutMapping("/conversations/{id}/archive")
    public Map<String, Boolean> archive(@PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean archived) {
        inbox.archive(id, archived);
        return Map.of("archived", archived);
    }

    /** The badge: unread inbound messages across every thread. */
    @GetMapping("/unread")
    public Map<String, Integer> unread() {
        return Map.of("unread", inbox.unreadTotal());
    }

    /**
     * The file attached to one message.
     *
     * <p>Served through the app rather than as a link to Meta on purpose: Meta's
     * media URLs expire within minutes and require the platform's access token,
     * which must never reach a browser.
     *
     * <p>{@code inline} so an image opens in the page and a PDF opens in the
     * viewer; the browser still offers to save it.
     */
    @GetMapping("/messages/{id}/media")
    @Operation(summary = "Download a file from a conversation")
    public ResponseEntity<byte[]> media(@PathVariable UUID id) {
        WhatsappInboxService.MediaFile file = inbox.media(id);
        String name = file.fileName() == null || file.fileName().isBlank()
                ? "file"
                : file.fileName();
        return ResponseEntity.ok()
                .contentType(contentType(file.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(name, StandardCharsets.UTF_8).toString())
                // The bytes never change once cached, and a thread re-renders
                // every few seconds while it is open.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(file.content());
    }

    /**
     * The stored mime, if it is one Spring can parse.
     *
     * <p>Meta reports things like {@code audio/ogg; codecs=opus}, and a value it
     * invents tomorrow must serve the file as a download rather than throw a 500
     * on the way out. The bytes are the point; the label is a hint.
     */
    private static MediaType contentType(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
