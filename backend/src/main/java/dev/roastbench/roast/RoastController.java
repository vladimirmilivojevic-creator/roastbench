package dev.roastbench.roast;

import dev.roastbench.analysis.RorCalculator;
import dev.roastbench.common.PageResponse;
import dev.roastbench.profile.ProfileService;
import dev.roastbench.roast.RoastDtos.AssignProfileRequest;
import dev.roastbench.roast.RoastDtos.CompareResponse;
import dev.roastbench.roast.RoastDtos.ComparisonDto;
import dev.roastbench.roast.RoastDtos.CurveDto;
import dev.roastbench.roast.RoastDtos.ImportPreviewDto;
import dev.roastbench.roast.RoastDtos.ImportRequest;
import dev.roastbench.roast.RoastDtos.RoastDetailDto;
import dev.roastbench.roast.RoastDtos.RoastSummaryDto;
import dev.roastbench.roast.RoastDtos.RoastUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/roasts")
@Tag(name = "Roasts", description = "Import roast logs, analyse them and compare with a reference roast")
public class RoastController {

    private final RoastService roasts;
    private final ProfileService profiles;

    public RoastController(RoastService roasts, ProfileService profiles) {
        this.roasts = roasts;
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "List roasts (filter, sort, page)",
            description = "sort is one of roastedAt, batchNo, name, dtr, loss, totalTime, dropTemp; "
                    + "direction is asc or desc")
    public PageResponse<RoastSummaryDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long profileId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "roastedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return roasts.list(new RoastService.Filter(q, profileId, lotId, from, to), page, size, sort, direction);
    }

    @GetMapping("/{id}")
    @Operation(summary = "A roast with its events, phases, development time ratio and roast loss")
    public RoastDetailDto get(@PathVariable long id) {
        return roasts.detail(id);
    }

    @GetMapping("/{id}/curve")
    @Operation(summary = "Bean and environment temperature samples with the rate of rise (C/min)")
    public CurveDto curve(@PathVariable long id,
                          @RequestParam(defaultValue = "30") double rorWindowSeconds) {
        return roasts.curve(id, rorWindowSeconds);
    }

    @GetMapping("/{id}/comparison")
    @Operation(summary = "Difference to a reference roast",
            description = "Without referenceId the reference roast of the roast's profile is used.")
    public ComparisonDto comparison(@PathVariable long id, @RequestParam(required = false) Long referenceId,
                                    @RequestParam(defaultValue = "30") double rorWindowSeconds) {
        return roasts.comparison(id, referenceId, rorWindowSeconds);
    }

    @GetMapping("/compare")
    @Operation(summary = "Curves, events and metrics of up to 10 roasts for an overlay chart")
    public CompareResponse compare(@RequestParam List<Long> ids,
                                   @RequestParam(defaultValue = "30") double rorWindowSeconds) {
        return roasts.compareMany(ids, rorWindowSeconds);
    }

    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse and analyse a CSV or Artisan .alog file without saving it")
    public ImportPreviewDto preview(@RequestPart("file") MultipartFile file) throws IOException {
        return roasts.preview(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import a roast log; green coffee of the given lots is deducted from stock")
    public RoastDetailDto importRoast(@RequestPart("file") MultipartFile file,
                                      @RequestPart(value = "request", required = false) @Valid ImportRequest request)
            throws IOException {
        return roasts.importRoast(file.getOriginalFilename(), file.getBytes(), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit name, notes, roasted weight or marked events; metrics are recalculated")
    public RoastDetailDto update(@PathVariable long id, @Valid @RequestBody RoastUpdateRequest request) {
        return roasts.update(id, request);
    }

    @PutMapping("/{id}/profile")
    @Operation(summary = "Assign the roast to a profile (null removes it)")
    public RoastDetailDto assignProfile(@PathVariable long id, @RequestBody AssignProfileRequest request) {
        profiles.assignRoast(id, request.profileId());
        return roasts.detail(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a roast; its green coffee goes back into stock")
    public void delete(@PathVariable long id) {
        roasts.delete(id);
    }
}
