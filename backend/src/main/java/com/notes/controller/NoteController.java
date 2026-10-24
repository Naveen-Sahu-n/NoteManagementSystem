package com.notes.controller;

import com.notes.dto.NoteRequest;
import com.notes.dto.VisibilityRequest;
import com.notes.model.Note;
import com.notes.pdf.PdfStorageService;
import com.notes.service.NoteNotFoundException;
import com.notes.service.NoteService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final PdfStorageService pdfStorageService;

    public NoteController(
            NoteService noteService,
            PdfStorageService pdfStorageService) {

        this.noteService = noteService;
        this.pdfStorageService = pdfStorageService;
    }

    @PostMapping
    public Note createNote(
            @RequestBody NoteRequest request,
            Authentication authentication) {

        return noteService.createNote(
                authentication.getName(), request
        );
    }

    @GetMapping
    public List<Note> getNotes(Authentication authentication) {
        return noteService.getNotes(authentication.getName());
    }

    @GetMapping("/{id}")
    public Note getNote(
            @PathVariable Long id,
            Authentication authentication) {

        return noteService.getNote(
                authentication.getName(), id
        );
    }

    @PutMapping("/{id}")
    public Note updateNote(
            @PathVariable Long id,
            @RequestBody NoteRequest request,
            Authentication authentication) {

        return noteService.updateNote(
                authentication.getName(), id, request
        );
    }

    @PatchMapping("/{id}/visibility")
    public Note changeVisibility(
            @PathVariable Long id,
            @RequestBody VisibilityRequest request,
            Authentication authentication) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request is required."
            );
        }

        return noteService.changeVisibility(
                authentication.getName(),
                id,
                request.getVisibility()
        );
    }

    @PostMapping(
            value = "/pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Note uploadPdf(
            @RequestParam("title") String title,
            @RequestParam(
                    value = "description",
                    required = false
            ) String description,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        return noteService.createPdfNote(
                authentication.getName(),
                title,
                description,
                file
        );
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> viewPdf(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        return getPdfResponse(
                id, authentication, false
        );
    }

    @GetMapping("/{id}/pdf/download")
    public ResponseEntity<Resource> downloadPdf(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        return getPdfResponse(
                id, authentication, true
        );
    }

    private ResponseEntity<Resource> getPdfResponse(
            Long id,
            Authentication authentication,
            boolean download) throws IOException {

        // Check note ownership/visibility before accessing the file.
        Note note = noteService.getNote(
                authentication.getName(), id
        );

        if (note.getPdfStorageName() == null) {
            throw new NoteNotFoundException(
                    "PDF attachment not found."
            );
        }

        Path path = pdfStorageService.resolveStoredPath(
                note.getPdfStorageName()
        );

        if (!Files.isRegularFile(path)) {
            throw new NoteNotFoundException(
                    "PDF attachment not found."
            );
        }

        String filename = note.getOriginalFileName();

        if (filename == null || filename.isBlank()) {
            filename = "note.pdf";
        }

        ContentDisposition disposition =
                (download
                        ? ContentDisposition.attachment()
                        : ContentDisposition.inline())
                        .filename(filename, StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(Files.size(path))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{id}/pdf")
    public ResponseEntity<Void> deletePdf(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        noteService.deletePdfAttachment(
                authentication.getName(), id
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public String deleteNote(
            @PathVariable Long id,
            Authentication authentication) {

        noteService.deleteNote(
                authentication.getName(), id
        );

        return "Note deleted successfully.";
    }
}