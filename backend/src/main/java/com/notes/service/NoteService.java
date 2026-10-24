
package com.notes.service;

import com.notes.dto.NoteRequest;
import com.notes.model.Note;
import com.notes.model.User;
import com.notes.pdf.PdfStorageService;
import com.notes.pdf.PdfStorageService.QuarantinedPdf;
import com.notes.pdf.PdfStorageService.StoredPdf;
import com.notes.repository.NoteRepository;
import com.notes.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class NoteService {

    private static final Logger LOGGER =
            Logger.getLogger(NoteService.class.getName());

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final PdfStorageService pdfStorageService;

    public NoteService(
            NoteRepository noteRepository,
            UserRepository userRepository,
            PdfStorageService pdfStorageService) {

        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.pdfStorageService = pdfStorageService;
    }

    private User getUser(String username) {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }

        return user;
    }

    public Note createNote(String username, NoteRequest request) {

        validate(request);

        User user = getUser(username);
        String now = OffsetDateTime.now().toString();

        Note note = new Note(
                null,
                user.getId(),
                request.getTitle().trim(),
                request.getContent(),
                "PRIVATE",
                now,
                now
        );

        note.setDescription("");

        Note saved = noteRepository.save(note);

        if (saved == null) {
            throw new IllegalStateException(
                    "Note was saved, but its ID could not be retrieved."
            );
        }

        return saved;
    }

    public Note createPdfNote(
            String username,
            String title,
            String description,
            MultipartFile file) throws IOException {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }

        if (title.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Title must not exceed 200 characters."
            );
        }

        if (description != null && description.length() > 5000) {
            throw new IllegalArgumentException(
                    "Description must not exceed 5000 characters."
            );
        }

        User user = getUser(username);
        StoredPdf stored = pdfStorageService.store(file);

        try {
            String now = OffsetDateTime.now().toString();

            Note note = new Note(
                    null,
                    user.getId(),
                    title.trim(),
                    "",
                    "PRIVATE",
                    now,
                    now
            );

            note.setDescription(
                    description == null ? "" : description.trim()
            );
            note.setPdfStorageName(stored.storageName());
            note.setOriginalFileName(stored.originalFilename());
            note.setFileSize(stored.fileSize());
            note.setContentType("application/pdf");

            Note saved = noteRepository.save(note);

            if (saved == null) {
                throw new IllegalStateException(
                        "Could not retrieve the created PDF note."
                );
            }

            return saved;

        } catch (RuntimeException ex) {
            try {
                pdfStorageService.delete(stored.storageName());
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
                LOGGER.log(
                        Level.SEVERE,
                        "Could not clean up a PDF after note creation failed.",
                        cleanupException
                );
            }

            throw ex;
        }
    }

    public List<Note> getNotes(String username) {
        User user = getUser(username);
        return noteRepository.findVisibleToUser(user.getId());
    }

    public Note getNote(String username, Long noteId) {

        User user = getUser(username);

        Note note = noteRepository.findAccessibleById(
                noteId, user.getId()
        );

        if (note == null) {
            throw new NoteNotFoundException("Note not found.");
        }

        return note;
    }

    public Note updateNote(
            String username,
            Long noteId,
            NoteRequest request) {

        validate(request);

        User user = getUser(username);

        Note existing = noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );

        if (existing == null) {
            throw new NoteNotFoundException("Note not found.");
        }

        boolean updated = noteRepository.update(
                noteId,
                user.getId(),
                request.getTitle().trim(),
                request.getContent(),
                OffsetDateTime.now().toString()
        );

        if (!updated) {
            throw new NoteNotFoundException("Note not found.");
        }

        return noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );
    }

    public Note changeVisibility(
            String username,
            Long noteId,
            String requestedVisibility) {

        User user = getUser(username);

        if (requestedVisibility == null) {
            throw new IllegalArgumentException(
                    "Visibility is required."
            );
        }

        String visibility =
                requestedVisibility.trim().toUpperCase(Locale.ROOT);

        if (!visibility.equals("PRIVATE")
                && !visibility.equals("PUBLIC")) {

            throw new IllegalArgumentException(
                    "Visibility must be PRIVATE or PUBLIC."
            );
        }

        Note existing = noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );

        if (existing == null) {
            throw new NoteNotFoundException("Note not found.");
        }

        boolean updated = noteRepository.updateVisibility(
                noteId,
                user.getId(),
                visibility,
                OffsetDateTime.now().toString()
        );

        if (!updated) {
            throw new NoteNotFoundException("Note not found.");
        }

        return noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );
    }

    public void deletePdfAttachment(
            String username,
            Long noteId) throws IOException {

        User user = getUser(username);

        Note note = noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );

        if (note == null) {
            throw new NoteNotFoundException("Note not found.");
        }

        String storageName = note.getPdfStorageName();

        if (storageName == null) {
            throw new IllegalArgumentException(
                    "This note has no PDF attachment."
            );
        }

        QuarantinedPdf quarantined =
                pdfStorageService.quarantine(storageName);

        try {
            boolean cleared = noteRepository.clearPdfAttachment(
                    noteId,
                    user.getId(),
                    OffsetDateTime.now().toString()
            );

            if (!cleared) {
                throw new NoteNotFoundException("Note not found.");
            }

        } catch (RuntimeException ex) {
            restoreAfterFailure(quarantined, ex);
            throw ex;
        }

        discardAfterSuccess(quarantined);
    }

    public void deleteNote(String username, Long noteId) {

        User user = getUser(username);

        Note note = noteRepository.findByIdAndUserId(
                noteId, user.getId()
        );

        if (note == null) {
            throw new NoteNotFoundException("Note not found.");
        }

        QuarantinedPdf quarantined = null;

        try {
            if (note.getPdfStorageName() != null) {
                quarantined = pdfStorageService.quarantine(
                        note.getPdfStorageName()
                );
            }

            boolean deleted = noteRepository.delete(
                    noteId, user.getId()
            );

            if (!deleted) {
                throw new NoteNotFoundException("Note not found.");
            }

        } catch (IOException ex) {
            restoreAfterFailure(quarantined, ex);

            throw new IllegalStateException(
                    "Could not safely prepare the PDF for deletion.",
                    ex
            );

        } catch (RuntimeException ex) {
            restoreAfterFailure(quarantined, ex);
            throw ex;
        }

        discardAfterSuccess(quarantined);
    }

    private void restoreAfterFailure(
            QuarantinedPdf quarantined,
            Exception originalException) {

        if (quarantined == null) {
            return;
        }

        try {
            pdfStorageService.restore(quarantined);
        } catch (IOException restoreException) {
            originalException.addSuppressed(restoreException);

            LOGGER.log(
                    Level.SEVERE,
                    "CRITICAL: Could not restore a quarantined PDF.",
                    restoreException
            );
        }
    }

    private void discardAfterSuccess(QuarantinedPdf quarantined) {

        if (quarantined == null) {
            return;
        }

        try {
            pdfStorageService.discard(quarantined);
        } catch (IOException ex) {
            /*
             * The database operation has already succeeded.
             * Keep the inaccessible quarantine file for later cleanup.
             */
            LOGGER.log(
                    Level.WARNING,
                    "A quarantined PDF could not be permanently deleted.",
                    ex
            );
        }
    }

    private void validate(NoteRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request is required."
            );
        }

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {

            throw new IllegalArgumentException("Title is required.");
        }

        if (request.getTitle().trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Title must not exceed 200 characters."
            );
        }

        if (request.getContent() == null
                || request.getContent().isBlank()) {

            throw new IllegalArgumentException(
                    "Content is required."
            );
        }
    }
}
