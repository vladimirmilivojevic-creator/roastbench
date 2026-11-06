package dev.roastbench.profile;

import dev.roastbench.profile.ProfileService.ProfileDto;
import dev.roastbench.profile.ProfileService.ProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profiles", description = "Roast profiles and their reference roast")
public class ProfileController {

    public record ReferenceRequest(Long roastId) {
    }

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List profiles with roast count and average DTR, loss and time")
    public List<ProfileDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProfileDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileDto create(@Valid @RequestBody ProfileRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProfileDto update(@PathVariable long id, @Valid @RequestBody ProfileRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a profile; its roasts are kept without a profile")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/reference")
    @Operation(summary = "Set the reference roast of a profile (null clears it)")
    public ProfileDto setReference(@PathVariable long id, @RequestBody ReferenceRequest request) {
        return service.setReference(id, request.roastId());
    }
}
